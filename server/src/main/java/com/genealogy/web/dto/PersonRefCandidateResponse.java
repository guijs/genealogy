package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.genealogy.domain.projection.ProjectionPerson;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PersonRefCandidateResponse {
    @JsonProperty("person_id")
    private final String personId;

    @JsonProperty("display_name")
    private final String displayName;

    private final boolean deceased;

    public PersonRefCandidateResponse(String personId, String displayName, boolean deceased) {
        this.personId = personId;
        this.displayName = displayName;
        this.deceased = deceased;
    }

    public static PersonRefCandidateResponse fromProjectionPerson(ProjectionPerson person) {
        boolean deceased = person.getDeathYear() != null;
        return new PersonRefCandidateResponse(
                person.getId().toString(),
                person.getDisplayName(),
                deceased
        );
    }

    public String getPersonId() {
        return personId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isDeceased() {
        return deceased;
    }
}
