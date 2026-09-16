package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.genealogy.domain.story.StoryComment;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommentResponse {
    private static final DateTimeFormatter INSTANT_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final String id;

    @JsonProperty("story_id")
    private final String storyId;

    @JsonProperty("author_user_id")
    private final String authorUserId;

    private final String body;

    private final List<MentionResponse> mentions;

    @JsonProperty("created_at")
    private final String createdAt;

    @JsonProperty("updated_at")
    private final String updatedAt;

    public CommentResponse(String id, String storyId, String authorUserId, String body,
                           List<MentionResponse> mentions, String createdAt, String updatedAt) {
        this.id = id;
        this.storyId = storyId;
        this.authorUserId = authorUserId;
        this.body = body;
        this.mentions = mentions;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static CommentResponse fromComment(StoryComment comment) {
        List<MentionResponse> mentionResponses = comment.getMentions().stream()
                .map(MentionResponse::fromMention)
                .collect(Collectors.toList());

        return new CommentResponse(
                comment.getId().toString(),
                comment.getStoryId().toString(),
                comment.getAuthorUserId().toString(),
                comment.getBody(),
                mentionResponses.isEmpty() ? null : mentionResponses,
                comment.getCreatedAt() != null ? INSTANT_FORMATTER.format(comment.getCreatedAt()) : null,
                comment.getUpdatedAt() != null ? INSTANT_FORMATTER.format(comment.getUpdatedAt()) : null
        );
    }

    public String getId() {
        return id;
    }

    public String getStoryId() {
        return storyId;
    }

    public String getAuthorUserId() {
        return authorUserId;
    }

    public String getBody() {
        return body;
    }

    public List<MentionResponse> getMentions() {
        return mentions;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }
}
