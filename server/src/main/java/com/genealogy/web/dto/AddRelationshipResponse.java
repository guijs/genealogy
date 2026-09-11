package com.genealogy.web.dto;

public class AddRelationshipResponse {
    private final boolean success;

    public AddRelationshipResponse(boolean success) {
        this.success = success;
    }

    public boolean isSuccess() {
        return success;
    }
}
