package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class UpdateStoryRequest {
    private String title;
    private String body;

    @JsonProperty("narrative_time")
    private String narrativeTime;

    @JsonProperty("person_ids")
    private List<String> personIds;

    private Integer version;

    public UpdateStoryRequest() {}

    public UpdateStoryRequest(String title, String body, String narrativeTime,
                              List<String> personIds, Integer version) {
        this.title = title;
        this.body = body;
        this.narrativeTime = narrativeTime;
        this.personIds = personIds;
        this.version = version;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getNarrativeTime() {
        return narrativeTime;
    }

    public void setNarrativeTime(String narrativeTime) {
        this.narrativeTime = narrativeTime;
    }

    public List<String> getPersonIds() {
        return personIds;
    }

    public void setPersonIds(List<String> personIds) {
        this.personIds = personIds;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
