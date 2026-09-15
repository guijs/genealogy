package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RelationshipDissolveResponse {
    private final String id;
    private final boolean dissolved;

    public RelationshipDissolveResponse(String id, boolean dissolved) {
        this.id = id;
        this.dissolved = dissolved;
    }

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    @JsonProperty("dissolved")
    public boolean isDissolved() {
        return dissolved;
    }
}
