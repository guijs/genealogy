package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SetProgenitorResponse {
    @JsonProperty("progenitor_person_id")
    private final String progenitorPersonId;

    public SetProgenitorResponse(String progenitorPersonId) {
        this.progenitorPersonId = progenitorPersonId;
    }

    public String getProgenitorPersonId() {
        return progenitorPersonId;
    }
}
