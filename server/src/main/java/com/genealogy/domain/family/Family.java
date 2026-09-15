package com.genealogy.domain.family;

import java.util.UUID;

public class Family {
    private final UUID id;
    private final String name;
    private final UUID progenitorPersonId;

    public Family(UUID id, String name) {
        this(id, name, null);
    }

    public Family(UUID id, String name, UUID progenitorPersonId) {
        this.id = id;
        this.name = name;
        this.progenitorPersonId = progenitorPersonId;
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
}
