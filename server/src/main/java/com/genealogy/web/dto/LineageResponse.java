package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class LineageResponse {
    @JsonProperty("family_id")
    private final String familyId;
    
    @JsonProperty("progenitor_person_id")
    private final String progenitorPersonId;
    
    private final List<LineageGenerationDTO> generations;

    @JsonProperty("generation_name_align")
    private final String generationNameAlign;

    public LineageResponse(String familyId, String progenitorPersonId, List<LineageGenerationDTO> generations) {
        this(familyId, progenitorPersonId, generations, "A");
    }

    public LineageResponse(String familyId, String progenitorPersonId, List<LineageGenerationDTO> generations, String generationNameAlign) {
        this.familyId = familyId;
        this.progenitorPersonId = progenitorPersonId;
        this.generations = generations;
        this.generationNameAlign = generationNameAlign;
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

    public String getGenerationNameAlign() {
        return generationNameAlign;
    }
}
