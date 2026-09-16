package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class FamilyResponse {
    private final String id;
    private final String name;
    
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonProperty("progenitor_person_id")
    private final String progenitorPersonId;

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonProperty("generation_names")
    private final List<String> generationNames;

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonProperty("generation_name_align")
    private final String generationNameAlign;

    public FamilyResponse(String id, String name) {
        this(id, name, null, null, "A");
    }

    public FamilyResponse(String id, String name, String progenitorPersonId) {
        this(id, name, progenitorPersonId, null, "A");
    }

    public FamilyResponse(String id, String name, String progenitorPersonId, 
                          List<String> generationNames, String generationNameAlign) {
        this.id = id;
        this.name = name;
        this.progenitorPersonId = progenitorPersonId;
        this.generationNames = generationNames;
        this.generationNameAlign = generationNameAlign;
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

    public List<String> getGenerationNames() {
        return generationNames;
    }

    public String getGenerationNameAlign() {
        return generationNameAlign;
    }
}
