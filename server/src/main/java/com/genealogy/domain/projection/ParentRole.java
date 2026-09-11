package com.genealogy.domain.projection;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ParentRole {
    FATHER("father"),
    MOTHER("mother"),
    PARENT("parent");

    private final String value;

    ParentRole(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
