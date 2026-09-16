package com.genealogy.domain.story;

import java.time.Instant;
import java.util.UUID;

public class CommentPersonRef {
    private final UUID id;
    private final UUID commentId;
    private final UUID personId;
    private final String displayNameSnapshot;
    private final Instant createdAt;
    private final PersonRefStatus status;

    public CommentPersonRef(UUID id, UUID commentId, UUID personId, String displayNameSnapshot,
                            Instant createdAt, PersonRefStatus status) {
        this.id = id;
        this.commentId = commentId;
        this.personId = personId;
        this.displayNameSnapshot = displayNameSnapshot;
        this.createdAt = createdAt;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCommentId() {
        return commentId;
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
