package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class GenerationNamesRequest {
    @JsonProperty("generation_names")
    private List<String> generationNames;

    @JsonProperty("generation_name_align")
    private String generationNameAlign;

    public GenerationNamesRequest() {
    }

    public GenerationNamesRequest(List<String> generationNames, String generationNameAlign) {
        this.generationNames = generationNames;
        this.generationNameAlign = generationNameAlign;
    }

    public List<String> getGenerationNames() {
        return generationNames;
    }

    public void setGenerationNames(List<String> generationNames) {
        this.generationNames = generationNames;
    }

    public String getGenerationNameAlign() {
        return generationNameAlign;
    }

    public void setGenerationNameAlign(String generationNameAlign) {
        this.generationNameAlign = generationNameAlign;
    }
}
