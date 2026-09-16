package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PersonRefRequest {
    @JsonProperty("person_id")
    private String personId;

    @JsonProperty("display_name_snapshot")
    private String displayNameSnapshot;

    public PersonRefRequest() {
    }

    public PersonRefRequest(String personId, String displayNameSnapshot) {
        this.personId = personId;
        this.displayNameSnapshot = displayNameSnapshot;
    }

    public String getPersonId() {
        return personId;
    }

    public void setPersonId(String personId) {
        this.personId = personId;
    }

    public String getDisplayNameSnapshot() {
        return displayNameSnapshot;
    }

    public void setDisplayNameSnapshot(String displayNameSnapshot) {
        this.displayNameSnapshot = displayNameSnapshot;
    }
}
