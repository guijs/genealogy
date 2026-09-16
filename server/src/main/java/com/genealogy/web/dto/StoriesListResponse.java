package com.genealogy.web.dto;

import java.util.List;

public class StoriesListResponse {
    private final List<StoryResponse> stories;

    public StoriesListResponse(List<StoryResponse> stories) {
        this.stories = stories;
    }

    public List<StoryResponse> getStories() {
        return stories;
    }
}
