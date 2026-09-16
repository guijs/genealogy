package com.genealogy.domain.family;

import java.util.List;
import java.util.UUID;

public class Family {
    private final UUID id;
    private final String name;
    private final UUID progenitorPersonId;
    private final List<String> generationNames;
    private final String generationNameAlign;

    public Family(UUID id, String name) {
        this(id, name, null, null, "A");
    }

    public Family(UUID id, String name, UUID progenitorPersonId) {
        this(id, name, progenitorPersonId, null, "A");
    }

    public Family(UUID id, String name, UUID progenitorPersonId, List<String> generationNames, String generationNameAlign) {
        this.id = id;
        this.name = name;
        this.progenitorPersonId = progenitorPersonId;
        this.generationNames = generationNames;
        this.generationNameAlign = generationNameAlign != null ? generationNameAlign : "A";
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UUID getProgenitorPersonId() {
        return progenitorPersonId;
    }

    public List<String> getGenerationNames() {
        return generationNames;
    }

    public String getGenerationNameAlign() {
        return generationNameAlign;
    }
}
