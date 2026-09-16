package com.genealogy.web.dto;

import java.util.List;

public class CreateCommentRequest {
    private String body;
    private List<MentionRequest> mentions;

    public CreateCommentRequest() {
    }

    public CreateCommentRequest(String body) {
        this.body = body;
    }

    public CreateCommentRequest(String body, List<MentionRequest> mentions) {
        this.body = body;
        this.mentions = mentions;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public List<MentionRequest> getMentions() {
        return mentions;
    }

    public void setMentions(List<MentionRequest> mentions) {
        this.mentions = mentions;
    }
}
