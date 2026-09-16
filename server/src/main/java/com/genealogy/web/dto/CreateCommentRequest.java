package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CreateCommentRequest {
    private String body;

    @JsonProperty("parent_comment_id")
    private String parentCommentId;

    public CreateCommentRequest() {
    }

    public CreateCommentRequest(String body, String parentCommentId) {
        this.body = body;
        this.parentCommentId = parentCommentId;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getParentCommentId() {
        return parentCommentId;
    }

    public void setParentCommentId(String parentCommentId) {
        this.parentCommentId = parentCommentId;
    }
}
