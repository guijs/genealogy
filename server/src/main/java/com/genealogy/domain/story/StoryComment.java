package com.genealogy.domain.story;

import java.time.Instant;
import java.util.UUID;

public class StoryComment {
    private final UUID id;
    private final UUID storyId;
    private final UUID authorUserId;
    private final String body;
    private final Instant createdAt;
    private final Instant updatedAt;

    public StoryComment(UUID id, UUID storyId, UUID authorUserId, String body,
                        Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.storyId = storyId;
        this.authorUserId = authorUserId;
        this.body = body;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStoryId() {
        return storyId;
    }

    public UUID getAuthorUserId() {
        return authorUserId;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
