package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class LineageResponse {
    @JsonProperty("family_id")
    private final String familyId;
    
    @JsonProperty("progenitor_person_id")
    private final String progenitorPersonId;
    
    private final List<LineageGenerationDTO> generations;

    public LineageResponse(String familyId, String progenitorPersonId, List<LineageGenerationDTO> generations) {
        this.familyId = familyId;
        this.progenitorPersonId = progenitorPersonId;
        this.generations = generations;
    }

    public String getFamilyId() {
        return familyId;
    }

    public String getProgenitorPersonId() {
        return progenitorPersonId;
    }

    public List<LineageGenerationDTO> getGenerations() {
        return generations;
    }
}
