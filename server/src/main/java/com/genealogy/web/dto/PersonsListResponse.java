package com.genealogy.web.dto;

import java.util.List;

public class PersonsListResponse {
    private final List<PersonResponse> persons;

    public PersonsListResponse(List<PersonResponse> persons) {
        this.persons = persons;
    }

    public List<PersonResponse> getPersons() {
        return persons;
    }
}
