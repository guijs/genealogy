package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.genealogy.domain.story.CommentMention;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MentionResponse {
    @JsonProperty("user_id")
    private final String userId;

    @JsonProperty("display_name_snapshot")
    private final String displayNameSnapshot;

    private final String status;

    public MentionResponse(String userId, String displayNameSnapshot, String status) {
        this.userId = userId;
        this.displayNameSnapshot = displayNameSnapshot;
        this.status = status;
    }

    public static MentionResponse fromMention(CommentMention mention) {
        String status;
        if (mention.getUserId() == null) {
            status = "removed";
        } else if (!mention.isCurrentMember()) {
            status = "left";
        } else {
            status = "active";
        }

        return new MentionResponse(
                mention.getUserId() != null ? mention.getUserId().toString() : null,
                mention.getDisplayNameSnapshot(),
                status
        );
    }

    public String getUserId() {
        return userId;
    }

    public String getDisplayNameSnapshot() {
        return displayNameSnapshot;
    }

    public String getStatus() {
        return status;
    }
}
