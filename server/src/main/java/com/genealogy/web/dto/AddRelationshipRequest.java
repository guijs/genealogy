package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AddRelationshipRequest {
    @JsonProperty("parent_id")
    private String parentId;

    @JsonProperty("child_id")
    private String childId;

    @JsonProperty("relationship_type")
    private String relationshipType;

    public AddRelationshipRequest() {
    }

    public AddRelationshipRequest(String parentId, String childId, String relationshipType) {
        this.parentId = parentId;
        this.childId = childId;
        this.relationshipType = relationshipType;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getChildId() {
        return childId;
    }

    public void setChildId(String childId) {
        this.childId = childId;
    }

    public String getRelationshipType() {
        return relationshipType;
    }

    public void setRelationshipType(String relationshipType) {
        this.relationshipType = relationshipType;
    }
}
