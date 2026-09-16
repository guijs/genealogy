package com.genealogy.web.dto;

public class CreateCommentRequest {
    private String body;

    public CreateCommentRequest() {
    }

    public CreateCommentRequest(String body) {
        this.body = body;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
