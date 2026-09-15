package com.genealogy.store;

import com.genealogy.config.MediaStorageProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.UUID;

@Component
@Primary
@ConditionalOnProperty(name = "media.storage", havingValue = "local", matchIfMissing = true)
public class LocalObjectStore implements ObjectStore {

    private final MediaStorageProperties properties;

    public LocalObjectStore(MediaStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public String generateUploadUrl(String key, String mimeType, long maxSize) {
        UUID familyId = extractFamilyIdFromKey(key);
        long expiryEpochSeconds = Instant.now().getEpochSecond() + (properties.getUploadTokenExpiryMinutes() * 60L);

        MediaUploadToken token = new MediaUploadToken(key, mimeType, maxSize, familyId, expiryEpochSeconds);
        String encodedToken = token.encode(properties.getUploadSecret());

        String baseUrl = getBaseUrl();
        return baseUrl + "/api/v1/media/uploads/" + encodedToken;
    }

    public MediaUploadToken validateToken(String encodedToken) {
        MediaUploadToken token = MediaUploadToken.decode(encodedToken, properties.getUploadSecret());
        if (token == null) {
            return null;
        }
        if (token.isExpired()) {
            return null;
        }
        return token;
    }

    public void storeFile(String storageKey, InputStream inputStream, long contentLength) throws IOException {
        Path filePath = resolveAndValidatePath(storageKey);

        Files.createDirectories(filePath.getParent());
        Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
    }

    public boolean fileExists(String storageKey) {
        Path filePath = resolveAndValidatePath(storageKey);
        return Files.exists(filePath);
    }

    public Path getFilePath(String storageKey) {
        return resolveAndValidatePath(storageKey);
    }

    /**
     * Resolves the storage key against the configured root and validates that
     * the resulting path is within the storage root. Prevents path traversal
     * attacks using "../" sequences or absolute paths.
     *
     * @param storageKey the storage key to resolve
     * @return the validated absolute path
     * @throws PathTraversalException if the resolved path escapes the storage root
     */
    private Path resolveAndValidatePath(String storageKey) {
        Path rootPath = Paths.get(properties.getLocal().getRoot()).toAbsolutePath().normalize();
        Path resolvedPath = rootPath.resolve(storageKey).toAbsolutePath().normalize();

        if (!resolvedPath.startsWith(rootPath)) {
            throw new PathTraversalException(
                "Invalid storage key: path traversal attempt detected - resolved path escapes storage root");
        }

        return resolvedPath;
    }

    private UUID extractFamilyIdFromKey(String key) {
        String[] parts = key.split("/");
        if (parts.length >= 2 && "families".equals(parts[0])) {
            try {
                return UUID.fromString(parts[1]);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid storage key format: cannot extract family ID");
            }
        }
        throw new IllegalArgumentException("Invalid storage key format: expected families/{familyId}/...");
    }

    private String getBaseUrl() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            String scheme = request.getScheme();
            String serverName = request.getServerName();
            int serverPort = request.getServerPort();

            StringBuilder url = new StringBuilder();
            url.append(scheme).append("://").append(serverName);

            if (("http".equals(scheme) && serverPort != 80) ||
                ("https".equals(scheme) && serverPort != 443)) {
                url.append(":").append(serverPort);
            }

            return url.toString();
        }
        return "http://localhost:8080";
    }
}
