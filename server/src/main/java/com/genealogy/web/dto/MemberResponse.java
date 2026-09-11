package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MemberResponse {
    @JsonProperty("user_id")
    private final String userId;

    private final String role;

    public MemberResponse(String userId, String role) {
        this.userId = userId;
        this.role = role;
    }

    public String getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }
}
