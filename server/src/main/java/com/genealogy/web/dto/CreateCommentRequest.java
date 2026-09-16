package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class CreateCommentRequest {
    private String body;
    private List<MentionRequest> mentions;

    @JsonProperty("person_refs")
    private List<PersonRefRequest> personRefs;

    public CreateCommentRequest() {
    }

    public CreateCommentRequest(String body) {
        this.body = body;
    }

    public CreateCommentRequest(String body, List<MentionRequest> mentions) {
        this(body, mentions, null);
    }

    public CreateCommentRequest(String body, List<MentionRequest> mentions, List<PersonRefRequest> personRefs) {
        this.body = body;
        this.mentions = mentions;
        this.personRefs = personRefs;
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
    }
}
