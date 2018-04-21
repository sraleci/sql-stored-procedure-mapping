package com.laryeaquaye.SqlStoredProcedureMapping;

enum SqlStoredProcedureMappingCliOption {
    MAP_STORED_PROCEDURE("map",
            "Given a stored procedure, construct a usage tree of all stored procedures where the given is the base.",
            "map [baseStoredProcedure.sql]"),
    INVENTORY_FUNCTIONS("functions",
            "Determine a list of functions used only within the tree of stored procedures for a given base stored procedure. Functions are sourced from a provided directory location.",
            "functions [baseStoredProcedure.sql] [directory/of/functions/]"),
    HELP("help", "Get a list of available functions with their definitions and usage.", "help");

    final String optionValue;
    final String description;
    final String usage;

    SqlStoredProcedureMappingCliOption(String optionValue, String description, String usage) {
        this.optionValue = optionValue;
        this.description = description;
        this.usage = usage;
    }

    String getNotEnoughArgumentsError() {
        return String.format("Not enough arguments passed for %s. Usage below:\n%s", optionValue, usage);
    }
}
