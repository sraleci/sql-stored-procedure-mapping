package com.laryeaquaye.SqlStoredProcedureMapping;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class StoredProcedureTreeNode {
    final StoredProcedureTreeNode parent;
    final List<StoredProcedureTreeNode> executed = new ArrayList<>();
    final File file;
    private final boolean alreadyReferenced;

    StoredProcedureTreeNode(StoredProcedureTreeNode parent, File file, boolean alreadyReferenced) {
        this.parent = parent;
        this.file = file;
        this.alreadyReferenced = alreadyReferenced;
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder(file.getName());

        stringBuilder.append(alreadyReferenced ? " (circular reference)" : "");

        StringBuilder indentation = new StringBuilder();
        for (StoredProcedureTreeNode current = this; current != null; current = current.parent) {
            indentation.append("\t");
        }

        for (StoredProcedureTreeNode child : executed) {
            stringBuilder.append(String.format("\n%s-> %s", indentation, child != null ? child.toString() : "<null>"));
        }

        return stringBuilder.toString();
    }
}
