package com.genealogy.domain.family;

public enum Role {
    ADMIN("admin"),
    EDITOR("editor"),
    VIEWER("viewer");

    private final String value;

    Role(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public boolean canWrite() {
        return this == ADMIN || this == EDITOR;
    }

    public boolean canRead() {
        return true;
    }
}
