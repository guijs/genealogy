package com.genealogy.web;

import com.genealogy.domain.family.Role;
import com.genealogy.store.FamilyStore;
import com.genealogy.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MediaControllerTest extends BaseIntegrationTest {

    @Autowired
    private FamilyStore familyStore;

    private static final UUID FAMILY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID VIEWER_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID NON_MEMBER_USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final String FAMILY_NAME = "Test Family";

    @BeforeEach
    void setUp() {
        cleanAllData();
        familyStore.createFamily(FAMILY_ID, FAMILY_NAME);
        familyStore.addMemberWithRole(FAMILY_ID, ADMIN_USER_ID, Role.ADMIN);
        familyStore.addMemberWithRole(FAMILY_ID, VIEWER_USER_ID, Role.VIEWER);
    }

    // Test 1: No auth → 401
    @Test
    void uploadUrl_noAuth_returns401() throws Exception {
        String body = """
            {"mime_type": "image/jpeg", "file_size": 1024}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/media/upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("authentication required"));
    }

    // Test 2: Non-member → 404
    @Test
    void uploadUrl_nonMember_returns404() throws Exception {
        String body = """
            {"mime_type": "image/jpeg", "file_size": 1024}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/media/upload-url")
                        .header("X-User-Id", NON_MEMBER_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    // Test 3: Viewer → 403 (write access required)
    @Test
    void uploadUrl_viewer_returns403() throws Exception {
        String body = """
            {"mime_type": "image/jpeg", "file_size": 1024}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/media/upload-url")
                        .header("X-User-Id", VIEWER_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("write access required"));
    }

    // Test 4: Valid jpeg → 200 with server-generated key
    @Test
    void uploadUrl_validJpeg_returns200WithServerKey() throws Exception {
        String body = """
            {"mime_type": "image/jpeg", "file_size": 1024}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/media/upload-url")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upload_url").exists())
                .andExpect(jsonPath("$.storage_key").value(startsWith("families/" + FAMILY_ID + "/media/")))
                .andExpect(jsonPath("$.storage_key").value(matchesPattern("families/" + FAMILY_ID + "/media/[a-f0-9-]+\\.jpg")));
    }

    // Test 5: Valid png → 200
    @Test
    void uploadUrl_validPng_returns200() throws Exception {
        String body = """
            {"mime_type": "image/png", "file_size": 2048}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/media/upload-url")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storage_key").value(matchesPattern("families/" + FAMILY_ID + "/media/[a-f0-9-]+\\.png")));
    }

    // Test 6: Valid webp → 200
    @Test
    void uploadUrl_validWebp_returns200() throws Exception {
        String body = """
            {"mime_type": "image/webp", "file_size": 3072}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/media/upload-url")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storage_key").value(matchesPattern("families/" + FAMILY_ID + "/media/[a-f0-9-]+\\.webp")));
    }

    // Test 7: Case-insensitive MIME type → 200
    @Test
    void uploadUrl_caseInsensitiveMimeType_returns200() throws Exception {
        String body = """
            {"mime_type": "IMAGE/JPEG", "file_size": 1024}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/media/upload-url")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storage_key").value(matchesPattern("families/" + FAMILY_ID + "/media/[a-f0-9-]+\\.jpg")));
    }

    // Test 8: Client-supplied storage_key → 400
    @Test
    void uploadUrl_clientStorageKey_returns400() throws Exception {
        String body = """
            {"mime_type": "image/jpeg", "file_size": 1024, "storage_key": "malicious/path/file.jpg"}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/media/upload-url")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("client-supplied storage key not allowed"));
    }

    // Test 9: Invalid MIME type → 400
    @Test
    void uploadUrl_invalidMimeType_returns400() throws Exception {
        String body = """
            {"mime_type": "application/pdf", "file_size": 1024}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/media/upload-url")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid MIME type: only jpeg, png, webp allowed"));
    }

    // Test 10: File size > 5MB → 400
    @Test
    void uploadUrl_fileTooLarge_returns400() throws Exception {
        long sizeTooLarge = 5 * 1024 * 1024 + 1; // 5MB + 1 byte
        String body = """
            {"mime_type": "image/jpeg", "file_size": %d}
            """.formatted(sizeTooLarge);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/media/upload-url")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("file too large: maximum 5MB allowed"));
    }

    // Test 11: File size exactly 5MB → 200 (boundary condition)
    @Test
    void uploadUrl_fileExactly5MB_returns200() throws Exception {
        long sizeExact = 5 * 1024 * 1024; // 5MB
        String body = """
            {"mime_type": "image/jpeg", "file_size": %d}
            """.formatted(sizeExact);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/media/upload-url")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upload_url").exists());
    }

    // Test 12: Editor can upload → 200
    @Test
    void uploadUrl_editor_returns200() throws Exception {
        UUID editorUserId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        familyStore.addMemberWithRole(FAMILY_ID, editorUserId, Role.EDITOR);

        String body = """
            {"mime_type": "image/jpeg", "file_size": 1024}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/media/upload-url")
                        .header("X-User-Id", editorUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upload_url").exists());
    }

    // Test 13: Empty MIME type → 400
    @Test
    void uploadUrl_emptyMimeType_returns400() throws Exception {
        String body = """
            {"mime_type": "", "file_size": 1024}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/media/upload-url")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid MIME type: only jpeg, png, webp allowed"));
    }

    // Test 14: Unknown family → 404
    @Test
    void uploadUrl_unknownFamily_returns404() throws Exception {
        UUID unknownFamilyId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        String body = """
            {"mime_type": "image/jpeg", "file_size": 1024}
            """;

        mockMvc.perform(post("/api/v1/families/" + unknownFamilyId + "/media/upload-url")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }
}
