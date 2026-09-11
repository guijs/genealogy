package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CreateUnionRequest {
    @JsonProperty("partner_a_id")
    private String partnerAId;

    @JsonProperty("partner_b_id")
    private String partnerBId;

    @JsonProperty("started_at")
    private String startedAt;

    public CreateUnionRequest() {}

    public CreateUnionRequest(String partnerAId, String partnerBId, String startedAt) {
        this.partnerAId = partnerAId;
        this.partnerBId = partnerBId;
        this.startedAt = startedAt;
    }

    public String getPartnerAId() {
        return partnerAId;
    }

    public void setPartnerAId(String partnerAId) {
        this.partnerAId = partnerAId;
    }

    public String getPartnerBId() {
        return partnerBId;
    }

    public void setPartnerBId(String partnerBId) {
        this.partnerBId = partnerBId;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }
}
