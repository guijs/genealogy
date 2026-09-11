package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.genealogy.domain.projection.MarriageStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MarriageDTO {
    private final String id;
    private final String[] partnerIds;
    private final MarriageStatus status;
    private final String startedAt;
    private final String endedAt;
    private final String endedReason;

    public MarriageDTO(String id, String[] partnerIds, MarriageStatus status,
                       String startedAt, String endedAt, String endedReason) {
        this.id = id;
        this.partnerIds = partnerIds;
        this.status = status;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.endedReason = endedReason;
    }

    public String getId() {
        return id;
    }

    public String[] getPartnerIds() {
        return partnerIds;
    }

    public MarriageStatus getStatus() {
        return status;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public String getEndedAt() {
        return endedAt;
    }

    public String getEndedReason() {
        return endedReason;
    }
}
