package com.genealogy.web.dto;

import java.util.List;

public class GenerationDTO {
    private final int index;
    private final List<GenerationPersonDTO> persons;

    public GenerationDTO(int index, List<GenerationPersonDTO> persons) {
        this.index = index;
        this.persons = persons;
    }

    public int getIndex() {
        return index;
    }

    public List<GenerationPersonDTO> getPersons() {
        return persons;
    }
}
