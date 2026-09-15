package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public class FamilyResponse {
    private final String id;
    private final String name;
    
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonProperty("progenitor_person_id")
    private final String progenitorPersonId;

    public FamilyResponse(String id, String name) {
        this(id, name, null);
    }

    public FamilyResponse(String id, String name, String progenitorPersonId) {
        this.id = id;
        this.name = name;
        this.progenitorPersonId = progenitorPersonId;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getProgenitorPersonId() {
        return progenitorPersonId;
    }
}
