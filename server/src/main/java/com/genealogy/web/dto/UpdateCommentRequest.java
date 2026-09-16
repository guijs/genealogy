package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class UpdateCommentRequest {
    private String body;
    private List<MentionRequest> mentions;

    @JsonProperty("person_refs")
    private List<PersonRefRequest> personRefs;

    private boolean personRefsProvided = false;

    @JsonProperty("updated_at")
    private String updatedAt;

    public UpdateCommentRequest() {
    }

    public UpdateCommentRequest(String body, String updatedAt) {
        this.body = body;
        this.updatedAt = updatedAt;
    }

    public UpdateCommentRequest(String body, List<MentionRequest> mentions, String updatedAt) {
        this(body, mentions, null, updatedAt);
    }

    public UpdateCommentRequest(String body, List<MentionRequest> mentions,
                                List<PersonRefRequest> personRefs, String updatedAt) {
        this.body = body;
        this.mentions = mentions;
        this.personRefs = personRefs;
        this.personRefsProvided = true;
        this.updatedAt = updatedAt;
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

    public List<PersonRefRequest> getPersonRefs() {
        return personRefs;
    }

    public void setPersonRefs(List<PersonRefRequest> personRefs) {
        this.personRefs = personRefs;
        this.personRefsProvided = true;
    }

    public boolean isPersonRefsProvided() {
        return personRefsProvided;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
