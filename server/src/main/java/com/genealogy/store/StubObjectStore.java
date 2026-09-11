package com.genealogy.store;

import org.springframework.stereotype.Component;

@Component
public class StubObjectStore implements ObjectStore {

    @Override
    public String generateUploadUrl(String key, String mimeType, long maxSize) {
        return "https://storage.example.com/upload?key=" + key;
    }
}
