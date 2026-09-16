package com.genealogy.domain.story;

import java.time.Instant;
import java.util.UUID;

public class CommentMention {
    private final UUID id;
    private final UUID commentId;
    private final UUID userId;
    private final String displayNameSnapshot;
    private final Instant createdAt;
    private final boolean isCurrentMember;

    public CommentMention(UUID id, UUID commentId, UUID userId, String displayNameSnapshot,
                          Instant createdAt, boolean isCurrentMember) {
        this.id = id;
        this.commentId = commentId;
        this.userId = userId;
        this.displayNameSnapshot = displayNameSnapshot;
        this.createdAt = createdAt;
        this.isCurrentMember = isCurrentMember;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCommentId() {
        return commentId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getDisplayNameSnapshot() {
        return displayNameSnapshot;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isCurrentMember() {
        return isCurrentMember;
    }
}
