package com.genealogy.store;

public interface ObjectStore {
    String generateUploadUrl(String key, String mimeType, long maxSize);
}
