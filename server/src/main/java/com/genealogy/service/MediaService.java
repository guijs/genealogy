package com.genealogy.service;

import com.genealogy.store.ObjectStore;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class MediaService {

    public static final long MAX_MEDIA_SIZE = 5L * 1024 * 1024; // 5MB

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final ObjectStore objectStore;

    public MediaService(ObjectStore objectStore) {
        this.objectStore = objectStore;
    }

    public UploadUrlResult getUploadUrl(UUID familyId, String mimeType, long fileSize, String clientStorageKey) {
        if (clientStorageKey != null && !clientStorageKey.isEmpty()) {
            return UploadUrlResult.error("client-supplied storage key not allowed");
        }

        String normalizedMimeType = mimeType != null 
                ? mimeType.toLowerCase(Locale.ROOT).trim() 
                : "";

        if (!ALLOWED_MIME_TYPES.contains(normalizedMimeType)) {
            return UploadUrlResult.error("invalid MIME type: only jpeg, png, webp allowed");
        }

        if (fileSize > MAX_MEDIA_SIZE) {
            return UploadUrlResult.error("file too large: maximum 5MB allowed");
        }

        String storageKey = generateStorageKey(familyId, normalizedMimeType);
        String uploadUrl = objectStore.generateUploadUrl(storageKey, normalizedMimeType, MAX_MEDIA_SIZE);

        return UploadUrlResult.success(uploadUrl, storageKey);
    }

    private String generateStorageKey(UUID familyId, String mimeType) {
        String ext = switch (mimeType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".bin";
        };
        return "families/" + familyId.toString() + "/media/" + UUID.randomUUID().toString() + ext;
    }

    public static class UploadUrlResult {
        private final boolean success;
        private final String uploadUrl;
        private final String storageKey;
        private final String error;

        private UploadUrlResult(boolean success, String uploadUrl, String storageKey, String error) {
            this.success = success;
            this.uploadUrl = uploadUrl;
            this.storageKey = storageKey;
            this.error = error;
        }

        public static UploadUrlResult success(String uploadUrl, String storageKey) {
            return new UploadUrlResult(true, uploadUrl, storageKey, null);
        }

        public static UploadUrlResult error(String error) {
            return new UploadUrlResult(false, null, null, error);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getUploadUrl() {
            return uploadUrl;
        }

        public String getStorageKey() {
            return storageKey;
        }

        public String getError() {
            return error;
        }
    }
}
