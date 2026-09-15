package com.genealogy.store;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

public class MediaUploadToken {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SEPARATOR = "|";

    private final String storageKey;
    private final String mimeType;
    private final long maxSize;
    private final UUID familyId;
    private final long expiryEpochSeconds;

    public MediaUploadToken(String storageKey, String mimeType, long maxSize, UUID familyId, long expiryEpochSeconds) {
        this.storageKey = storageKey;
        this.mimeType = mimeType;
        this.maxSize = maxSize;
        this.familyId = familyId;
        this.expiryEpochSeconds = expiryEpochSeconds;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getMaxSize() {
        return maxSize;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public long getExpiryEpochSeconds() {
        return expiryEpochSeconds;
    }

    public boolean isExpired() {
        return Instant.now().getEpochSecond() > expiryEpochSeconds;
    }

    public String encode(String secret) {
        String payload = String.join(SEPARATOR,
                storageKey,
                mimeType,
                String.valueOf(maxSize),
                familyId.toString(),
                String.valueOf(expiryEpochSeconds)
        );

        String signature = computeHmac(payload, secret);
        String tokenData = payload + SEPARATOR + signature;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenData.getBytes(StandardCharsets.UTF_8));
    }

    public static MediaUploadToken decode(String encodedToken, String secret) {
        if (encodedToken == null || encodedToken.isEmpty()) {
            return null;
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encodedToken);
            String tokenData = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = tokenData.split("\\|");

            if (parts.length != 6) {
                return null;
            }

            String storageKey = parts[0];
            String mimeType = parts[1];
            long maxSize = Long.parseLong(parts[2]);
            UUID familyId = UUID.fromString(parts[3]);
            long expiryEpochSeconds = Long.parseLong(parts[4]);
            String providedSignature = parts[5];

            String payload = String.join(SEPARATOR,
                    storageKey,
                    mimeType,
                    String.valueOf(maxSize),
                    familyId.toString(),
                    String.valueOf(expiryEpochSeconds)
            );

            String expectedSignature = computeHmac(payload, secret);

            if (!constantTimeEquals(expectedSignature, providedSignature)) {
                return null;
            }

            return new MediaUploadToken(storageKey, mimeType, maxSize, familyId, expiryEpochSeconds);

        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String computeHmac(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to compute HMAC", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
