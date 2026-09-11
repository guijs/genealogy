package com.genealogy.domain.family;

import java.util.UUID;

public class Family {
    private final UUID id;
    private final String name;

    public Family(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
