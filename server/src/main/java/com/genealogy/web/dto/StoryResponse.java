package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.genealogy.domain.story.Story;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class StoryResponse {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter INSTANT_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final String id;

    @JsonProperty("family_id")
    private final String familyId;

    private final String title;
    private final String body;

    @JsonProperty("person_ids")
    private final List<String> personIds;

    @JsonProperty("narrative_time")
    private final String narrativeTime;

    @JsonProperty("created_by")
    private final String createdBy;

    @JsonProperty("updated_by")
    private final String updatedBy;

    @JsonProperty("created_at")
    private final String createdAt;

    @JsonProperty("updated_at")
    private final String updatedAt;

    private final int version;

    public StoryResponse(String id, String familyId, String title, String body,
                         List<String> personIds, String narrativeTime,
                         String createdBy, String updatedBy,
                         String createdAt, String updatedAt, int version) {
        this.id = id;
        this.familyId = familyId;
        this.title = title;
        this.body = body;
        this.personIds = personIds;
        this.narrativeTime = narrativeTime;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static StoryResponse fromStory(Story story) {
        return new StoryResponse(
                story.getId().toString(),
                story.getFamilyId().toString(),
                story.getTitle(),
                story.getBody(),
                story.getPersonIds().stream().map(Object::toString).collect(Collectors.toList()),
                story.getNarrativeTime() != null ? story.getNarrativeTime().format(DATE_FORMATTER) : null,
                story.getCreatedBy().toString(),
                story.getUpdatedBy().toString(),
                story.getCreatedAt() != null ? INSTANT_FORMATTER.format(story.getCreatedAt()) : null,
                story.getUpdatedAt() != null ? INSTANT_FORMATTER.format(story.getUpdatedAt()) : null,
                story.getVersion()
        );
    }

    public String getId() {
        return id;
    }

    public String getFamilyId() {
        return familyId;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public List<String> getPersonIds() {
        return personIds;
    }

    public String getNarrativeTime() {
        return narrativeTime;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }
}
