package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LineagePersonDTO {
    private final String id;
    
    @JsonProperty("display_name")
    private final String displayName;
    
    private final boolean conflict;

    public LineagePersonDTO(String id, String displayName, boolean conflict) {
        this.id = id;
        this.displayName = displayName;
        this.conflict = conflict;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isConflict() {
        return conflict;
    }
}
