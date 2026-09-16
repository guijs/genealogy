package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class GenerationNamesResponse {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonProperty("generation_names")
    private final List<String> generationNames;

    @JsonProperty("generation_name_align")
    private final String generationNameAlign;

    public GenerationNamesResponse(List<String> generationNames, String generationNameAlign) {
        this.generationNames = generationNames;
        this.generationNameAlign = generationNameAlign != null ? generationNameAlign : "A";
    }

    public List<String> getGenerationNames() {
        return generationNames;
    }

    public String getGenerationNameAlign() {
        return generationNameAlign;
    }
}
