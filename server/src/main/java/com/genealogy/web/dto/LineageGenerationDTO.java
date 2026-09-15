package com.genealogy.web.dto;

import java.util.List;

public class LineageGenerationDTO {
    private final int index;
    private final List<LineagePersonDTO> persons;

    public LineageGenerationDTO(int index, List<LineagePersonDTO> persons) {
        this.index = index;
        this.persons = persons;
    }

    public int getIndex() {
        return index;
    }

    public List<LineagePersonDTO> getPersons() {
        return persons;
    }
}
