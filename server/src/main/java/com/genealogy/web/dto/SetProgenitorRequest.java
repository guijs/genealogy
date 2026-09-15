package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SetProgenitorRequest {
    @JsonProperty("person_id")
    private String personId;

    public SetProgenitorRequest() {
    }

    public SetProgenitorRequest(String personId) {
        this.personId = personId;
    }

    public String getPersonId() {
        return personId;
    }

    public void setPersonId(String personId) {
        this.personId = personId;
    }
}
