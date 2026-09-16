package com.genealogy.web.dto;

public class DeleteStoryRequest {
    private Integer version;

    public DeleteStoryRequest() {}

    public DeleteStoryRequest(Integer version) {
        this.version = version;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
