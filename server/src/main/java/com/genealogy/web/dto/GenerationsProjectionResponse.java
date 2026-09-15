package com.genealogy.web.dto;

import java.util.List;

public class GenerationsProjectionResponse {
    private final String familyId;
    private final String focusPersonId;
    private final List<GenerationDTO> generations;

    public GenerationsProjectionResponse(String familyId, String focusPersonId, List<GenerationDTO> generations) {
        this.familyId = familyId;
        this.focusPersonId = focusPersonId;
        this.generations = generations;
    }

    public String getFamilyId() {
        return familyId;
    }

    public String getFocusPersonId() {
        return focusPersonId;
    }

    public List<GenerationDTO> getGenerations() {
        return generations;
    }
}
