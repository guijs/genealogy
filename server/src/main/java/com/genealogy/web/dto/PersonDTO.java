package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.genealogy.domain.projection.Gender;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PersonDTO {
    private final String id;
    private final String displayName;
    private final Gender gender;
    private final Integer birthYear;
    private final Integer deathYear;
    private final Boolean deceased;

    public PersonDTO(String id, String displayName, Gender gender, Integer birthYear,
                     Integer deathYear, Boolean deceased) {
        this.id = id;
        this.displayName = displayName;
        this.gender = gender;
        this.birthYear = birthYear;
        this.deathYear = deathYear;
        this.deceased = (deceased != null && deceased) ? true : null;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Gender getGender() {
        return gender;
    }

    public Integer getBirthYear() {
        return birthYear;
    }

    public Integer getDeathYear() {
        return deathYear;
    }

    public Boolean getDeceased() {
        return deceased;
    }
}
