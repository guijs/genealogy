package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UploadUrlRequest {

    @JsonProperty("mime_type")
    private String mimeType;

    @JsonProperty("file_size")
    private Long fileSize;

    @JsonProperty("storage_key")
    private String storageKey;

    public UploadUrlRequest() {
    }

    public UploadUrlRequest(String mimeType, Long fileSize, String storageKey) {
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.storageKey = storageKey;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }
}
