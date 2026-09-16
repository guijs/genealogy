package com.genealogy.domain.story;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class Story {
    private final UUID id;
    private final UUID familyId;
    private final String title;
    private final String body;
    private final LocalDate narrativeTime;
    private final UUID createdBy;
    private final UUID updatedBy;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final int version;
    private final List<UUID> personIds;

    public Story(UUID id, UUID familyId, String title, String body, LocalDate narrativeTime,
                 UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt,
                 int version, List<UUID> personIds) {
        this.id = id;
        this.familyId = familyId;
        this.title = title;
        this.body = body;
        this.narrativeTime = narrativeTime;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
        this.personIds = personIds != null ? List.copyOf(personIds) : List.of();
    }

    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public LocalDate getNarrativeTime() {
        return narrativeTime;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }

    public List<UUID> getPersonIds() {
        return personIds;
    }

    public boolean isFamilyScoped() {
        return personIds.isEmpty();
    }
}
