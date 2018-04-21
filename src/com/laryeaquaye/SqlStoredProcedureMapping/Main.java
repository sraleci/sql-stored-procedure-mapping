package com.laryeaquaye.SqlStoredProcedureMapping;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Main {
    private static final String EXEC_PATTERN_REGEX = "exec ([_a-zA-Z0-9.]+)";
    private static final Pattern execPattern = Pattern.compile(EXEC_PATTERN_REGEX, Pattern.CASE_INSENSITIVE);

    private static final String FUNCTION_PATTERN_REGEX = "([a-zA-Z]+\\.fn[a-zA-Z0-9.]+)";
    private static final Pattern functionPattern = Pattern.compile(FUNCTION_PATTERN_REGEX, Pattern.CASE_INSENSITIVE);

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("No arguments were passed.");
        }

        SqlStoredProcedureMappingCliOption cliOption = Arrays.stream(SqlStoredProcedureMappingCliOption.values())
                .filter(x -> x.optionValue.equalsIgnoreCase(args[0]))
                .findFirst()
                .orElse(null);

        if (cliOption == null) {
            System.err.println("Invalid option passed. Use option help for more info.");
            return;
        }

        switch (cliOption) {
            case MAP_STORED_PROCEDURE:
                if (args.length < 2) {
                    System.err.println(cliOption.getNotEnoughArgumentsError());
                } else {
                    mapStoredProcedures(new File(args[1]));
                }
                return;
            case INVENTORY_FUNCTIONS:
                if (args.length < 3) {
                    cliOption.getNotEnoughArgumentsError();
                } else {
                    inventoryFunctions(new File(args[1]), new File(args[2]));
                }
                return;
            case HELP:
                System.out.println("SqlStoredProcedureMapping.java\n" +
                        "\tA tool to do stuff with a tree of stored procedures.\n" +
                        "\tHere's a list of options:");
                for (SqlStoredProcedureMappingCliOption optionHelp : SqlStoredProcedureMappingCliOption.values()) {
                    System.out.printf("\t\t%s - %s\n\t\t\tUsage: %s\n", optionHelp.optionValue, optionHelp.description, optionHelp.usage);
                }
                return;
            default:
                // CLI Option enum should have been bound and caught above. If this occurs, an enum option was probablyi
                // added without being implemented in the switch.
                throw new IllegalStateException();
        }
    }

    private static void mapStoredProcedures(File file) {
        if (!file.exists()) {
            throw new IllegalArgumentException(String.format("Path \"%s\" does not exist.", file.getPath()));
        }
        if (!file.isFile()) {
            throw new IllegalArgumentException(String.format("Path \"%s\" is not a file.", file.getPath()));
        }
        if (!file.getPath().toLowerCase().endsWith(".sql")) {
            throw new IllegalArgumentException(String.format("Path \"%s\" is not a SQL file.", file.getPath()));
        }

        StoredProcedureTreeNode node = getNodeAndChildren(file.getParentFile(), file.getPath(), null);
        if (node == null) {
            System.err.println("Stored procedure tree could not be created.");
            return;
        }

        System.out.printf("Tree:\n%s\n", node);

        Set<String> storedProcedureFiles = new HashSet<>();
        walkAppend(storedProcedureFiles, node, x -> x.file.getName());
        System.out.printf("Distinct stored procedures:\n%s\n", String.join(", ", new ArrayList<>(storedProcedureFiles)));
    }

    private static void inventoryFunctions(File baseStoredProcedure, File directoryOfFunctions) {
        if (!directoryOfFunctions.exists()) {
            throw new IllegalArgumentException(String.format("Path \"%s\" does not exist.", directoryOfFunctions.getPath()));
        }
        if (!directoryOfFunctions.isDirectory()) {
            throw new IllegalArgumentException(String.format("Path \"%s\" is not a directory.", directoryOfFunctions.getPath()));
        }

        // Get all sprocs
        StoredProcedureTreeNode node = getNodeAndChildren(baseStoredProcedure.getParentFile(), baseStoredProcedure.getPath(), null);
        Set<File> storedProcedureFiles = new HashSet<>();
        walkAppend(storedProcedureFiles, node, x -> x.file);

        // Get all functions from sprocs
        Set<String> functionsFromSprocs = new HashSet<>();
        for (File storedProcFile : storedProcedureFiles) {
            String sqlCode = fileToString(storedProcFile);
            if (sqlCode == null) {
                continue;
            }

            Matcher matcher = functionPattern.matcher(sqlCode);
            while (matcher.find()) {
                functionsFromSprocs.add(matcher.group(1));
            }
        }

        // Get all functions
        Set<String> allFunctions = Arrays.stream(Objects.requireNonNull(directoryOfFunctions.listFiles()))
                .filter(x -> x.getName().toLowerCase().endsWith(".sql"))
                .map(x -> {
                    String name = x.getName();
                    return name.substring(0, name.length() - 4);
                })
                .collect(Collectors.toSet());

        // Get all functions used elsewhere
        Set<String> storedProcedureFilePaths = storedProcedureFiles.stream()
                .map(File::getPath)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        Set<File> nonRelevantStoredProcedures = Arrays.stream(Objects.requireNonNull(baseStoredProcedure.getParentFile().listFiles()))
                .filter(x -> !storedProcedureFilePaths.contains(x.getPath().toLowerCase()))
                .collect(Collectors.toSet());
        Set<String> functionsInNonRelevantSprocs = new HashSet<>();
        for (File storedProcFile : nonRelevantStoredProcedures) {
            String sqlCode = fileToString(storedProcFile);
            if (sqlCode == null) {
                continue;
            }

            Matcher matcher = functionPattern.matcher(sqlCode);
            while (matcher.find()) {
                functionsInNonRelevantSprocs.add(matcher.group(1));
            }
        }

        // Inner join all functions and functions from sprocs collections
        for (String function : new HashSet<>(allFunctions)) {
            if (!functionsFromSprocs.contains(function)) {
                allFunctions.remove(function);
            }
        }

        // Left outer join result of inner join and non-relevant stored procedure functions
        for (String function : new HashSet<>(allFunctions)) {
            if (functionsInNonRelevantSprocs.contains(function)) {
                allFunctions.remove(function);
            }
        }

        // Sort and print
        System.out.println(String.join("\n", allFunctions.stream().sorted().collect(Collectors.toList())));
    }

    private static <T> void walkAppend(Set<T> set, StoredProcedureTreeNode node, Function<StoredProcedureTreeNode, T> transformBeforeAppend) {
        if (node == null) return;

        if (node.file != null) {
            set.add(transformBeforeAppend.apply(node));
        }

        for (StoredProcedureTreeNode childNode : node.executed) {
            walkAppend(set, childNode, transformBeforeAppend);
        }
    }

    private static StoredProcedureTreeNode getNodeAndChildren(File workDir, String fileName, StoredProcedureTreeNode parent) {
        File file = new File(fileName);
        if (!file.exists()) {
            System.err.println(String.format("Path \"%s\" does not exist.", file.getPath()));
            return null;
        }
        if (!file.isFile()) {
            System.err.println(String.format("Path \"%s\" is not a file.", file.getPath()));
            return null;
        }

        StoredProcedureTreeNode node = new StoredProcedureTreeNode(parent, file, false);

        String sqlCode = fileToString(file);
        if (sqlCode == null) {
            return null;
        }

        List<String> executedStoredProcedures = new ArrayList<>(0);
        Matcher matcher = execPattern.matcher(sqlCode);
        while (matcher.find()) {
            executedStoredProcedures.add(matcher.group(1));
        }

        for (String executedStoredProcedure: executedStoredProcedures) {
            String executedStoredProcedureFileName = String.format("%s.sql", executedStoredProcedure);
            boolean alreadyReferenced = false;

            for (StoredProcedureTreeNode currentNode = node; currentNode != null; currentNode = currentNode.parent) {
                if (executedStoredProcedureFileName.toLowerCase().equals(currentNode.file.getName().toLowerCase())) {
                    alreadyReferenced = true;
                    break;
                }
            }

            node.executed.add(alreadyReferenced
                    ? new StoredProcedureTreeNode(node, new File(executedStoredProcedureFileName), true)
                    : getNodeAndChildren(workDir, String.format("%s%s%s", workDir.getPath(), File.separator, executedStoredProcedureFileName), node));
        }

        while (node.executed.indexOf(null) != -1) {
            node.executed.remove(null);
        }

        return node;
    }

    private static String fileToString(File file) {
        if (!file.exists() || !file.isFile()) return null;

        StringBuilder sqlCodeBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String buffer;
            while ((buffer = reader.readLine()) != null) {
                sqlCodeBuilder.append(buffer);
            }
        } catch (IOException e) {
            System.err.printf("Error reading file : \"%s\".\n", file.getPath());
            return null;
        }
        return sqlCodeBuilder.toString();
    }
}
