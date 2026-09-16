package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.genealogy.domain.story.CommentPersonRef;
import com.genealogy.domain.story.StoryPersonRef;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PersonRefResponse {
    @JsonProperty("person_id")
    private final String personId;

    @JsonProperty("display_name_snapshot")
    private final String displayNameSnapshot;

    private final String status;

    public PersonRefResponse(String personId, String displayNameSnapshot, String status) {
        this.personId = personId;
        this.displayNameSnapshot = displayNameSnapshot;
        this.status = status;
    }

    public static PersonRefResponse fromStoryPersonRef(StoryPersonRef ref) {
        return new PersonRefResponse(
                ref.getPersonId() != null ? ref.getPersonId().toString() : null,
                ref.getDisplayNameSnapshot(),
                ref.getStatus().getValue()
        );
    }

    public static PersonRefResponse fromCommentPersonRef(CommentPersonRef ref) {
        return new PersonRefResponse(
                ref.getPersonId() != null ? ref.getPersonId().toString() : null,
                ref.getDisplayNameSnapshot(),
                ref.getStatus().getValue()
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
}
