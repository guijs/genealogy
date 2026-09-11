package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.genealogy.domain.projection.MarriageStatus;
import com.genealogy.domain.union.Union;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UnionResponse {
    private final String id;
    @JsonProperty("partner_ids")
    private final String[] partnerIds;
    private final MarriageStatus status;
    @JsonProperty("started_at")
    private final String startedAt;
    @JsonProperty("ended_at")
    private final String endedAt;
    @JsonProperty("ended_reason")
    private final String endedReason;

    public UnionResponse(String id, String[] partnerIds, MarriageStatus status,
                         String startedAt, String endedAt, String endedReason) {
        this.id = id;
        this.partnerIds = partnerIds;
        this.status = status;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.endedReason = endedReason;
    }

    public static UnionResponse fromUnion(Union union) {
        return new UnionResponse(
                union.getId().toString(),
                new String[]{union.getPartnerAId().toString(), union.getPartnerBId().toString()},
                union.getStatus(),
                union.getStartedAt(),
                union.getEndedAt(),
                union.getEndedReason()
        );
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
