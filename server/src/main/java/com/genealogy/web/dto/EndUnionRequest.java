package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EndUnionRequest {
    @JsonProperty("ended_reason")
    private String endedReason;

    @JsonProperty("ended_at")
    private String endedAt;

    public EndUnionRequest() {}

    public EndUnionRequest(String endedReason, String endedAt) {
        this.endedReason = endedReason;
        this.endedAt = endedAt;
    }

    public String getEndedReason() {
        return endedReason;
    }

    public void setEndedReason(String endedReason) {
        this.endedReason = endedReason;
    }

    public String getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(String endedAt) {
        this.endedAt = endedAt;
    }
}
