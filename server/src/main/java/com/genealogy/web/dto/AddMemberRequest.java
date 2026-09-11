package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AddMemberRequest {
    @JsonProperty("user_id")
    private String userId;

    private String role;

    public AddMemberRequest() {
    }

    public AddMemberRequest(String userId, String role) {
        this.userId = userId;
        this.role = role;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
