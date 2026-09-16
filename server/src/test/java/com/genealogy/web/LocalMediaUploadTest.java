package com.genealogy.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genealogy.config.MediaStorageProperties;
import com.genealogy.domain.family.Role;
import com.genealogy.store.FamilyStore;
import com.genealogy.store.LocalObjectStore;
import com.genealogy.store.MediaUploadToken;
import com.genealogy.support.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LocalMediaUploadTest extends BaseIntegrationTest {

    private static final String AUTH_HEADER = "Authorization";

    @Autowired
    private FamilyStore familyStore;

    @Autowired
    private LocalObjectStore localObjectStore;

    @Autowired
    private MediaStorageProperties mediaStorageProperties;

    @Autowired
    private ObjectMapper objectMapper;

    private static final UUID FAMILY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private Path mediaRoot;

    @BeforeEach
    void setUp() throws IOException {
        cleanAllData();
        familyStore.createFamily(FAMILY_ID, "Test Family");
        familyStore.addMemberWithRole(FAMILY_ID, ADMIN_USER_ID, Role.ADMIN);

        mediaRoot = Paths.get(mediaStorageProperties.getLocal().getRoot());
        if (Files.exists(mediaRoot)) {
            deleteDirectory(mediaRoot);
        }
        Files.createDirectories(mediaRoot);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (mediaRoot != null && Files.exists(mediaRoot)) {
            deleteDirectory(mediaRoot);
        }
    }

    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException ignored) {}
                    });
        }
    }

    @Test
    void happyPath_getUploadUrl_putBytes_fileExistsOnDisk() throws Exception {
        String requestBody = """
            {"mime_type": "image/jpeg", "file_size": 1024}
            """;

        MvcResult uploadUrlResult = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/media/upload-url")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upload_url").exists())
                .andExpect(jsonPath("$.storage_key").exists())
                .andReturn();

        String responseJson = uploadUrlResult.getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(responseJson);
        String uploadUrl = response.get("upload_url").asText();
        String storageKey = response.get("storage_key").asText();

        assertTrue(uploadUrl.contains("/api/v1/media/uploads/"), "upload_url should point to local upload endpoint");

        String token = uploadUrl.substring(uploadUrl.lastIndexOf('/') + 1);
        byte[] fileContent = new byte[1024];
        java.util.Arrays.fill(fileContent, (byte) 0xFF);

        mockMvc.perform(put("/api/v1/media/uploads/" + token)
                        .contentType("image/jpeg")
                        .content(fileContent))
                .andExpect(status().isCreated());

        assertTrue(localObjectStore.fileExists(storageKey), "File should exist on disk");

        Path filePath = localObjectStore.getFilePath(storageKey);
        byte[] storedContent = Files.readAllBytes(filePath);
        assertArrayEquals(fileContent, storedContent, "Stored content should match uploaded content");
    }

    @Test
    void putWithWrongMimeType_returns400() throws Exception {
        String requestBody = """
            {"mime_type": "image/jpeg", "file_size": 1024}
            """;

        MvcResult uploadUrlResult = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/media/upload-url")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = uploadUrlResult.getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(responseJson);
        String uploadUrl = response.get("upload_url").asText();
        String token = uploadUrl.substring(uploadUrl.lastIndexOf('/') + 1);

        byte[] fileContent = new byte[1024];

        mockMvc.perform(put("/api/v1/media/uploads/" + token)
                        .contentType("image/png")
                        .content(fileContent))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Content-Type mismatch: expected image/jpeg"));
    }

    @Test
    void putOversizedFile_returns413() throws Exception {
        String requestBody = """
            {"mime_type": "image/jpeg", "file_size": 1024}
            """;

        MvcResult uploadUrlResult = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/media/upload-url")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = uploadUrlResult.getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(responseJson);
        String uploadUrl = response.get("upload_url").asText();
        String token = uploadUrl.substring(uploadUrl.lastIndexOf('/') + 1);

        byte[] oversizedContent = new byte[6 * 1024 * 1024];

        mockMvc.perform(put("/api/v1/media/uploads/" + token)
                        .contentType("image/jpeg")
                        .content(oversizedContent))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void putWithInvalidToken_returns401() throws Exception {
        byte[] fileContent = new byte[1024];

        mockMvc.perform(put("/api/v1/media/uploads/invalid-token-here")
                        .contentType("image/jpeg")
                        .content(fileContent))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid or expired upload token"));
    }

    @Test
    void putWithExpiredToken_returns401() throws Exception {
        long expiredTime = Instant.now().getEpochSecond() - 60;
        String storageKey = "families/" + FAMILY_ID + "/media/" + UUID.randomUUID() + ".jpg";

        MediaUploadToken expiredToken = new MediaUploadToken(
                storageKey,
                "image/jpeg",
                5 * 1024 * 1024,
                FAMILY_ID,
                expiredTime
        );
        String encodedToken = expiredToken.encode(mediaStorageProperties.getUploadSecret());

        byte[] fileContent = new byte[1024];

        mockMvc.perform(put("/api/v1/media/uploads/" + encodedToken)
                        .contentType("image/jpeg")
                        .content(fileContent))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid or expired upload token"));
    }

    @Test
    void putWithTamperedToken_returns401() throws Exception {
        String requestBody = """
            {"mime_type": "image/jpeg", "file_size": 1024}
            """;

        MvcResult uploadUrlResult = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/media/upload-url")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = uploadUrlResult.getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(responseJson);
        String uploadUrl = response.get("upload_url").asText();
        String token = uploadUrl.substring(uploadUrl.lastIndexOf('/') + 1);

        String tamperedToken = token.substring(0, token.length() - 5) + "XXXXX";

        byte[] fileContent = new byte[1024];

        mockMvc.perform(put("/api/v1/media/uploads/" + tamperedToken)
                        .contentType("image/jpeg")
                        .content(fileContent))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid or expired upload token"));
    }

    @Test
    void putWithEmptyFile_returns400() throws Exception {
        String requestBody = """
            {"mime_type": "image/jpeg", "file_size": 1024}
            """;

        MvcResult uploadUrlResult = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/media/upload-url")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = uploadUrlResult.getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(responseJson);
        String uploadUrl = response.get("upload_url").asText();
        String token = uploadUrl.substring(uploadUrl.lastIndexOf('/') + 1);

        mockMvc.perform(put("/api/v1/media/uploads/" + token)
                        .contentType("image/jpeg")
                        .content(new byte[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("empty file not allowed"));
    }

    @Test
    void putWithoutContentType_returns400() throws Exception {
        String requestBody = """
            {"mime_type": "image/jpeg", "file_size": 1024}
            """;

        MvcResult uploadUrlResult = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/media/upload-url")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = uploadUrlResult.getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(responseJson);
        String uploadUrl = response.get("upload_url").asText();
        String token = uploadUrl.substring(uploadUrl.lastIndexOf('/') + 1);

        byte[] fileContent = new byte[1024];

        mockMvc.perform(put("/api/v1/media/uploads/" + token)
                        .content(fileContent))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void uploadUrl_generatesLocalUrl() throws Exception {
        String requestBody = """
            {"mime_type": "image/jpeg", "file_size": 1024}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/media/upload-url")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upload_url").exists())
                .andExpect(jsonPath("$.storage_key").exists());
    }

    @Test
    void putMultipleFiles_allStoredCorrectly() throws Exception {
        for (int i = 0; i < 3; i++) {
            String requestBody = """
                {"mime_type": "image/png", "file_size": 2048}
                """;

            MvcResult uploadUrlResult = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/media/upload-url")
                            .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andReturn();

            String responseJson = uploadUrlResult.getResponse().getContentAsString();
            JsonNode response = objectMapper.readTree(responseJson);
            String uploadUrl = response.get("upload_url").asText();
            String storageKey = response.get("storage_key").asText();
            String token = uploadUrl.substring(uploadUrl.lastIndexOf('/') + 1);

            byte[] fileContent = new byte[2048];
            java.util.Arrays.fill(fileContent, (byte) i);

            mockMvc.perform(put("/api/v1/media/uploads/" + token)
                            .contentType("image/png")
                            .content(fileContent))
                    .andExpect(status().isCreated());

            assertTrue(localObjectStore.fileExists(storageKey), "File " + i + " should exist on disk");
        }
    }

    @Test
    void putWithPathTraversalKey_returns400() throws Exception {
        long validExpiry = Instant.now().getEpochSecond() + 3600;
        String maliciousStorageKey = "../../../etc/passwd";

        MediaUploadToken traversalToken = new MediaUploadToken(
                maliciousStorageKey,
                "image/jpeg",
                5 * 1024 * 1024,
                FAMILY_ID,
                validExpiry
        );
        String encodedToken = traversalToken.encode(mediaStorageProperties.getUploadSecret());

        byte[] fileContent = new byte[1024];

        mockMvc.perform(put("/api/v1/media/uploads/" + encodedToken)
                        .contentType("image/jpeg")
                        .content(fileContent))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid storage key"));
    }
}
