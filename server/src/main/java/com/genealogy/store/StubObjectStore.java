package com.genealogy.store;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "media.storage", havingValue = "stub")
public class StubObjectStore implements ObjectStore {

    @Override
    public String generateUploadUrl(String key, String mimeType, long maxSize) {
        return "https://storage.example.com/upload?key=" + key;
    }
}
