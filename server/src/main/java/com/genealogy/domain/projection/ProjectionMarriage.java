package com.genealogy.domain.projection;

import java.util.UUID;

public class ProjectionMarriage {
    private final UUID id;
    private final UUID familyId;
    private final UUID partner1Id;
    private final UUID partner2Id;
    private final MarriageStatus status;
    private final String startedAt;
    private final String endedAt;
    private final String endedReason;

    public ProjectionMarriage(UUID id, UUID familyId, UUID partner1Id, UUID partner2Id,
                              MarriageStatus status, String startedAt, String endedAt, String endedReason) {
        this.id = id;
        this.familyId = familyId;
        this.partner1Id = partner1Id;
        this.partner2Id = partner2Id;
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

    public UUID getPartner1Id() {
        return partner1Id;
    }

    public UUID getPartner2Id() {
        return partner2Id;
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

    public ProjectionMarriage copy() {
        return new ProjectionMarriage(id, familyId, partner1Id, partner2Id, status, startedAt, endedAt, endedReason);
    }
}
