package com.genealogy.domain.projection;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ParentChildSubtype {
    BIOLOGICAL("biological"),
    ADOPTIVE("adoptive");

    private final String value;

    ParentChildSubtype(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
