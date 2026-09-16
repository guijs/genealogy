package com.genealogy.domain.story;

public enum PersonRefStatus {
    ACTIVE("active"),
    HIDDEN("hidden"),
    DELETED("deleted");

    private final String value;

    PersonRefStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
