package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class LineageGenerationDTO {
    private final int index;
    private final List<LineagePersonDTO> persons;
    
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonProperty("generation_name")
    private final String generationName;

    public LineageGenerationDTO(int index, List<LineagePersonDTO> persons) {
        this(index, persons, null);
    }

    public LineageGenerationDTO(int index, List<LineagePersonDTO> persons, String generationName) {
        this.index = index;
        this.persons = persons;
        this.generationName = generationName;
    }

    public int getIndex() {
        return index;
    }

    public List<LineagePersonDTO> getPersons() {
        return persons;
    }

    public String getGenerationName() {
        return generationName;
    }
}
