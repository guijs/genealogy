package com.genealogy.domain.story;

import java.time.Instant;
import java.util.UUID;

public class StoryPersonRef {
    private final UUID id;
    private final UUID storyId;
    private final UUID personId;
    private final String displayNameSnapshot;
    private final Instant createdAt;
    private final PersonRefStatus status;

    public StoryPersonRef(UUID id, UUID storyId, UUID personId, String displayNameSnapshot,
                          Instant createdAt, PersonRefStatus status) {
        this.id = id;
        this.storyId = storyId;
        this.personId = personId;
        this.displayNameSnapshot = displayNameSnapshot;
        this.createdAt = createdAt;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStoryId() {
        return storyId;
    }

    public UUID getPersonId() {
        return personId;
    }

    public String getDisplayNameSnapshot() {
        return displayNameSnapshot;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public PersonRefStatus getStatus() {
        return status;
    }
}
