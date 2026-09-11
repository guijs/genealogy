package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AuthResponse {
    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("token")
    private String token;

    public AuthResponse() {}

    public AuthResponse(String userId, String token) {
        this.userId = userId;
        this.token = token;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
