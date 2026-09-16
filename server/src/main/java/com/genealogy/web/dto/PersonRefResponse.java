package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.genealogy.domain.story.CommentPersonRef;
import com.genealogy.domain.story.PersonRefStatus;
import com.genealogy.domain.story.StoryPersonRef;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PersonRefResponse {
    @JsonProperty("person_id")
    private final String personId;

    @JsonProperty("display_name_snapshot")
    private final String displayNameSnapshot;

    private final String status;

    private final boolean clickable;

    public PersonRefResponse(String personId, String displayNameSnapshot, String status, boolean clickable) {
        this.personId = personId;
        this.displayNameSnapshot = displayNameSnapshot;
        this.status = status;
        this.clickable = clickable;
    }

    public static PersonRefResponse fromStoryPersonRef(StoryPersonRef ref) {
        boolean clickable = ref.getStatus() == PersonRefStatus.ACTIVE;
        return new PersonRefResponse(
                ref.getPersonId() != null ? ref.getPersonId().toString() : null,
                ref.getDisplayNameSnapshot(),
                ref.getStatus().getValue(),
                clickable
        );
    }

    public static PersonRefResponse fromCommentPersonRef(CommentPersonRef ref) {
        boolean clickable = ref.getStatus() == PersonRefStatus.ACTIVE;
        return new PersonRefResponse(
                ref.getPersonId() != null ? ref.getPersonId().toString() : null,
                ref.getDisplayNameSnapshot(),
                ref.getStatus().getValue(),
                clickable
        );
    }

    public String getPersonId() {
        return personId;
    }

    public String getDisplayNameSnapshot() {
        return displayNameSnapshot;
    }

    public String getStatus() {
        return status;
    }

    public boolean isClickable() {
        return clickable;
    }
}
