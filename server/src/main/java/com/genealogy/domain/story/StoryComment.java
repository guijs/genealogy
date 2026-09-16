package com.genealogy.domain.story;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class StoryComment {
    private final UUID id;
    private final UUID storyId;
    private final UUID authorUserId;
    private final String body;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final List<CommentMention> mentions;
    private final List<CommentPersonRef> personRefs;

    public StoryComment(UUID id, UUID storyId, UUID authorUserId, String body,
                        Instant createdAt, Instant updatedAt) {
        this(id, storyId, authorUserId, body, createdAt, updatedAt, Collections.emptyList(), Collections.emptyList());
    }

    public StoryComment(UUID id, UUID storyId, UUID authorUserId, String body,
                        Instant createdAt, Instant updatedAt, List<CommentMention> mentions) {
        this(id, storyId, authorUserId, body, createdAt, updatedAt, mentions, Collections.emptyList());
    }

    public StoryComment(UUID id, UUID storyId, UUID authorUserId, String body,
                        Instant createdAt, Instant updatedAt, List<CommentMention> mentions,
                        List<CommentPersonRef> personRefs) {
        this.id = id;
        this.storyId = storyId;
        this.authorUserId = authorUserId;
        this.body = body;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.mentions = mentions != null ? mentions : Collections.emptyList();
        this.personRefs = personRefs != null ? personRefs : Collections.emptyList();
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

    public List<CommentMention> getMentions() {
        return mentions;
    }

    public List<CommentPersonRef> getPersonRefs() {
        return personRefs;
    }
}
