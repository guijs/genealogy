package com.genealogy.domain.projection;

import java.util.UUID;

public class ProjectionRelationship {
    private final UUID id;
    private final UUID familyId;
    private final UUID parentId;
    private final UUID childId;
    private final ParentChildSubtype subtype;
    private final ParentRole role;
    private final UUID marriageId;
    private final boolean dissolved;

    public ProjectionRelationship(UUID id, UUID familyId, UUID parentId, UUID childId,
                                   ParentChildSubtype subtype, ParentRole role, UUID marriageId, Boolean dissolved) {
        this.id = id;
        this.familyId = familyId;
        this.parentId = parentId;
        this.childId = childId;
        this.subtype = subtype;
        this.role = role;
        this.marriageId = marriageId;
        this.dissolved = dissolved != null && dissolved;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public UUID getParentId() {
        return parentId;
    }

    public UUID getChildId() {
        return childId;
    }

    public ParentChildSubtype getSubtype() {
        return subtype;
    }

    public ParentRole getRole() {
        return role;
    }

    public UUID getMarriageId() {
        return marriageId;
    }

    public boolean isDissolved() {
        return dissolved;
    }

    public ProjectionRelationship copy() {
        return new ProjectionRelationship(id, familyId, parentId, childId, subtype, role, marriageId, dissolved);
    }
}
