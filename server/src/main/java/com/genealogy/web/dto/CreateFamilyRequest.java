package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CreateFamilyRequest {

    @JsonProperty("name")
    private String name;

    public CreateFamilyRequest() {
    }

    public CreateFamilyRequest(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
