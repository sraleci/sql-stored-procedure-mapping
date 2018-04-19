package com.laryeaquaye.SqlStoredProcedureMapping;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static final String EXEC_PATTERN_REGEX = "exec (dbo.[_a-zA-Z0-9.]*)";
    private static final Pattern execPattern = Pattern.compile(EXEC_PATTERN_REGEX, Pattern.CASE_INSENSITIVE);

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("No arguments were passed.");
        }

        File file = new File(args[0]);
        if (!file.exists()) {
            throw new IllegalArgumentException(String.format("Path \"%s\" does not exist.", file.getPath()));
        }
        if (!file.isFile()) {
            throw new IllegalArgumentException(String.format("Path \"%s\" is not a file.", file.getPath()));
        }
        if (!file.getPath().toLowerCase().endsWith(".sql")) {
            throw new IllegalArgumentException(String.format("Path \"%s\" is not a SQL file.", file.getPath()));
        }

        StoredProcedureTreeNode node = getNodeAndChildren(file.getParentFile(), args[0], null);
        System.out.printf("Tree:\n%s\n", node);

        Set<String> storedProcedureFiles = new HashSet<>();
        walkAppend(storedProcedureFiles, node);
        System.out.printf("Distinct stored procedures:\n%s\n", String.join(", ", new ArrayList<>(storedProcedureFiles)));
    }

    private static void walkAppend(Set<String> set, StoredProcedureTreeNode node) {
        if (node == null) return;

        if (node.file != null) {
            set.add(node.file.getName());
        }

        for (StoredProcedureTreeNode childNode : node.executed) {
            walkAppend(set, childNode);
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

        StringBuilder sqlCodeBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String buffer;
            while ((buffer = reader.readLine()) != null) {
                sqlCodeBuilder.append(buffer);
            }
        } catch (IOException e) {
            System.err.println("Error reading ");
            return null;
        }
        String sqlCode = sqlCodeBuilder.toString();

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

        return node;
    }
}

