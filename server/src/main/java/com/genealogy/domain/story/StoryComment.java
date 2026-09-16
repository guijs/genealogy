package com.genealogy.domain.story;

import java.time.Instant;
import java.util.UUID;

public class StoryComment {
    private final UUID id;
    private final UUID storyId;
    private final UUID parentCommentId;
    private final UUID authorUserId;
    private final String body;
    private final Instant createdAt;

    public StoryComment(UUID id, UUID storyId, UUID parentCommentId, UUID authorUserId,
                        String body, Instant createdAt) {
        this.id = id;
        this.storyId = storyId;
        this.parentCommentId = parentCommentId;
        this.authorUserId = authorUserId;
        this.body = body;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStoryId() {
        return storyId;
    }

    public UUID getParentCommentId() {
        return parentCommentId;
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

    public boolean isRootComment() {
        return parentCommentId == null;
    }
}
