package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.genealogy.domain.projection.ParentChildSubtype;
import com.genealogy.domain.projection.ParentRole;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RelationshipDTO {
    private final String id;
    private final String type;
    private final ParentChildSubtype subtype;
    private final String parentId;
    private final String childId;
    private final ParentRole role;
    private final String marriageId;

    public RelationshipDTO(String id, String type, ParentChildSubtype subtype,
                           String parentId, String childId, ParentRole role, String marriageId) {
        this.id = id;
        this.type = type;
        this.subtype = subtype;
        this.parentId = parentId;
        this.childId = childId;
        this.role = role;
        this.marriageId = marriageId;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public ParentChildSubtype getSubtype() {
        return subtype;
    }

    public String getParentId() {
        return parentId;
    }

    public String getChildId() {
        return childId;
    }

    public ParentRole getRole() {
        return role;
    }

    public String getMarriageId() {
        return marriageId;
    }
}
