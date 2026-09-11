package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UploadUrlResponse {

    @JsonProperty("upload_url")
    private final String uploadUrl;

    @JsonProperty("storage_key")
    private final String storageKey;

    public UploadUrlResponse(String uploadUrl, String storageKey) {
        this.uploadUrl = uploadUrl;
        this.storageKey = storageKey;
    }

    public String getUploadUrl() {
        return uploadUrl;
    }

    public String getStorageKey() {
        return storageKey;
    }
}
