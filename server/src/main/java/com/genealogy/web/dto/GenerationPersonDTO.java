package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenerationPersonDTO {
    private final String id;
    private final String displayName;
    private final Boolean conflict;

    public GenerationPersonDTO(String id, String displayName, boolean conflict) {
        this.id = id;
        this.displayName = displayName;
        this.conflict = conflict ? true : null;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Boolean getConflict() {
        return conflict;
    }
}
