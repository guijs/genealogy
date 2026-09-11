package com.genealogy.domain.projection;

import com.fasterxml.jackson.annotation.JsonValue;

public enum MarriageStatus {
    ACTIVE("active"),
    DIVORCED("divorced"),
    WIDOWED("widowed"),
    ENDED("ended");

    private final String value;

    MarriageStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
