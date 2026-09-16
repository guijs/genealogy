package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UpdateCommentRequest {
    private String body;

    @JsonProperty("updated_at")
    private String updatedAt;

    public UpdateCommentRequest() {
    }

    public UpdateCommentRequest(String body, String updatedAt) {
        this.body = body;
        this.updatedAt = updatedAt;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
