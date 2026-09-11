package com.genealogy.domain.union;

import com.genealogy.domain.projection.MarriageStatus;

import java.util.UUID;

public class Union {
    private final UUID id;
    private final UUID familyId;
    private final UUID partnerAId;
    private final UUID partnerBId;
    private MarriageStatus status;
    private final String startedAt;
    private String endedAt;
    private String endedReason;

    public Union(UUID id, UUID familyId, UUID partnerAId, UUID partnerBId,
                 MarriageStatus status, String startedAt, String endedAt, String endedReason) {
        this.id = id;
        this.familyId = familyId;
        this.partnerAId = partnerAId;
        this.partnerBId = partnerBId;
        this.status = status;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.endedReason = endedReason;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public UUID getPartnerAId() {
        return partnerAId;
    }

    public UUID getPartnerBId() {
        return partnerBId;
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

    public boolean involvesPartner(UUID personId) {
        return partnerAId.equals(personId) || partnerBId.equals(personId);
    }

    public boolean isActive() {
        return status == MarriageStatus.ACTIVE;
    }

    public void end(String endedReason, String endedAt) {
        if (endedReason == null || endedReason.isEmpty()) {
            this.endedReason = "ended";
        } else {
            this.endedReason = endedReason;
        }
        this.endedAt = endedAt;
        this.status = mapReasonToStatus(this.endedReason);
    }

    private MarriageStatus mapReasonToStatus(String reason) {
        if (reason == null) {
            return MarriageStatus.ENDED;
        }
        return switch (reason.toLowerCase()) {
            case "divorced" -> MarriageStatus.DIVORCED;
            case "widowed" -> MarriageStatus.WIDOWED;
            default -> MarriageStatus.ENDED;
        };
    }

    public Union copy() {
        return new Union(id, familyId, partnerAId, partnerBId, status, startedAt, endedAt, endedReason);
    }
}
