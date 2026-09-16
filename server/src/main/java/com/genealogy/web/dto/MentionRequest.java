package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MentionRequest {
    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("display_name_snapshot")
    private String displayNameSnapshot;

    public MentionRequest() {
    }

    public MentionRequest(String userId, String displayNameSnapshot) {
        this.userId = userId;
        this.displayNameSnapshot = displayNameSnapshot;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDisplayNameSnapshot() {
        return displayNameSnapshot;
    }

    public void setDisplayNameSnapshot(String displayNameSnapshot) {
        this.displayNameSnapshot = displayNameSnapshot;
    }
}
