package com.genealogy.domain.kinship;

import java.util.UUID;

public class Relation {
    private final UUID parentId;
    private final UUID childId;
    private final RelationType type;

    public Relation(UUID parentId, UUID childId, RelationType type) {
        this.parentId = parentId;
        this.childId = childId;
        this.type = type;
    }

    public UUID getParentId() {
        return parentId;
    }

    public UUID getChildId() {
        return childId;
    }

    public RelationType getType() {
        return type;
    }
}
