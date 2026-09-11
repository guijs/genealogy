package com.genealogy.web;

import com.genealogy.domain.family.Role;
import com.genealogy.store.FamilyStore;
import com.genealogy.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MembersControllerTest extends BaseIntegrationTest {

    private static final UUID ADMIN_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EDITOR_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID VIEWER_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID NON_MEMBER_USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID NEW_USER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Autowired
    private FamilyStore familyStore;

    private UUID familyId;

    @BeforeEach
    void setUp() {
        cleanAllData();
        familyId = UUID.randomUUID();
        familyStore.createFamily(familyId, "Test Family");
        familyStore.addMemberWithRole(familyId, ADMIN_USER_ID, Role.ADMIN);
        familyStore.addMemberWithRole(familyId, EDITOR_USER_ID, Role.EDITOR);
        familyStore.addMemberWithRole(familyId, VIEWER_USER_ID, Role.VIEWER);
    }

    @Test
    void addMember_withoutAuth_returns401() throws Exception {
        String body = """
            {"user_id": "%s", "role": "viewer"}
            """.formatted(NEW_USER_ID);

        mockMvc.perform(post("/api/v1/families/" + familyId + "/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("authentication required"));
    }

    @Test
    void addMember_nonMember_returns404() throws Exception {
        String body = """
            {"user_id": "%s", "role": "viewer"}
            """.formatted(NEW_USER_ID);

        mockMvc.perform(post("/api/v1/families/" + familyId + "/members")
                        .header("X-User-Id", NON_MEMBER_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    @Test
    void addMember_viewer_returns403() throws Exception {
        String body = """
            {"user_id": "%s", "role": "viewer"}
            """.formatted(NEW_USER_ID);

        mockMvc.perform(post("/api/v1/families/" + familyId + "/members")
                        .header("X-User-Id", VIEWER_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("admin access required"));
    }

    @Test
    void addMember_editor_returns403() throws Exception {
        String body = """
            {"user_id": "%s", "role": "viewer"}
            """.formatted(NEW_USER_ID);

        mockMvc.perform(post("/api/v1/families/" + familyId + "/members")
                        .header("X-User-Id", EDITOR_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("admin access required"));
    }

    @Test
    void addMember_adminAddsViewer_returns201() throws Exception {
        String body = """
            {"user_id": "%s", "role": "viewer"}
            """.formatted(NEW_USER_ID);

        mockMvc.perform(post("/api/v1/families/" + familyId + "/members")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user_id").value(NEW_USER_ID.toString()))
                .andExpect(jsonPath("$.role").value("viewer"));
    }

    @Test
    void addMember_adminAddsEditor_returns201() throws Exception {
        String body = """
            {"user_id": "%s", "role": "editor"}
            """.formatted(NEW_USER_ID);

        mockMvc.perform(post("/api/v1/families/" + familyId + "/members")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user_id").value(NEW_USER_ID.toString()))
                .andExpect(jsonPath("$.role").value("editor"));
    }

    @Test
    void addMember_adminAddsAdmin_returns201() throws Exception {
        String body = """
            {"user_id": "%s", "role": "admin"}
            """.formatted(NEW_USER_ID);

        mockMvc.perform(post("/api/v1/families/" + familyId + "/members")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user_id").value(NEW_USER_ID.toString()))
                .andExpect(jsonPath("$.role").value("admin"));
    }

    @Test
    void addMember_newUserCanGetFamily_afterAdded() throws Exception {
        String body = """
            {"user_id": "%s", "role": "viewer"}
            """.formatted(NEW_USER_ID);

        mockMvc.perform(post("/api/v1/families/" + familyId + "/members")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/" + familyId)
                        .header("X-User-Id", NEW_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(familyId.toString()))
                .andExpect(jsonPath("$.name").value("Test Family"));
    }

    @Test
    void addMember_duplicateMember_returns409() throws Exception {
        String body = """
            {"user_id": "%s", "role": "editor"}
            """.formatted(VIEWER_USER_ID);

        mockMvc.perform(post("/api/v1/families/" + familyId + "/members")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("user is already a member"));
    }

    @Test
    void addMember_invalidRole_returns400() throws Exception {
        String body = """
            {"user_id": "%s", "role": "superuser"}
            """.formatted(NEW_USER_ID);

        mockMvc.perform(post("/api/v1/families/" + familyId + "/members")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid role"));
    }

    @Test
    void addMember_missingRole_returns400() throws Exception {
        String body = """
            {"user_id": "%s"}
            """.formatted(NEW_USER_ID);

        mockMvc.perform(post("/api/v1/families/" + familyId + "/members")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("role is required"));
    }

    @Test
    void addMember_emptyRole_returns400() throws Exception {
        String body = """
            {"user_id": "%s", "role": ""}
            """.formatted(NEW_USER_ID);

        mockMvc.perform(post("/api/v1/families/" + familyId + "/members")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("role is required"));
    }

    @Test
    void addMember_missingUserId_returns400() throws Exception {
        String body = """
            {"role": "viewer"}
            """;

        mockMvc.perform(post("/api/v1/families/" + familyId + "/members")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("user_id is required"));
    }

    @Test
    void addMember_emptyUserId_returns400() throws Exception {
        String body = """
            {"user_id": "", "role": "viewer"}
            """;

        mockMvc.perform(post("/api/v1/families/" + familyId + "/members")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("user_id is required"));
    }

    @Test
    void addMember_invalidUserId_returns400() throws Exception {
        String body = """
            {"user_id": "not-a-uuid", "role": "viewer"}
            """;

        mockMvc.perform(post("/api/v1/families/" + familyId + "/members")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid user_id"));
    }

    @Test
    void addMember_nonExistentFamily_returns404() throws Exception {
        UUID nonExistentFamilyId = UUID.randomUUID();
        String body = """
            {"user_id": "%s", "role": "viewer"}
            """.formatted(NEW_USER_ID);

        mockMvc.perform(post("/api/v1/families/" + nonExistentFamilyId + "/members")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    @Test
    void addMember_caseInsensitiveRole_returns201() throws Exception {
        String body = """
            {"user_id": "%s", "role": "VIEWER"}
            """.formatted(NEW_USER_ID);

        mockMvc.perform(post("/api/v1/families/" + familyId + "/members")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user_id").value(NEW_USER_ID.toString()))
                .andExpect(jsonPath("$.role").value("viewer"));
    }

    @Test
    void listMembers_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + familyId + "/members"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("authentication required"));
    }

    @Test
    void listMembers_nonMember_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + familyId + "/members")
                        .header("X-User-Id", NON_MEMBER_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    @Test
    void listMembers_viewerCanList_returnsMembersIncludingSelf() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + familyId + "/members")
                        .header("X-User-Id", VIEWER_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members").isArray())
                .andExpect(jsonPath("$.members.length()").value(3))
                .andExpect(jsonPath("$.members[?(@.user_id == '%s')].role".formatted(ADMIN_USER_ID)).value("admin"))
                .andExpect(jsonPath("$.members[?(@.user_id == '%s')].role".formatted(EDITOR_USER_ID)).value("editor"))
                .andExpect(jsonPath("$.members[?(@.user_id == '%s')].role".formatted(VIEWER_USER_ID)).value("viewer"));
    }

    @Test
    void listMembers_afterAddMember_showsNewRow() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + familyId + "/members")
                        .header("X-User-Id", ADMIN_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(3));

        String body = """
            {"user_id": "%s", "role": "viewer"}
            """.formatted(NEW_USER_ID);

        mockMvc.perform(post("/api/v1/families/" + familyId + "/members")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/" + familyId + "/members")
                        .header("X-User-Id", ADMIN_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(4))
                .andExpect(jsonPath("$.members[?(@.user_id == '%s')].role".formatted(NEW_USER_ID)).value("viewer"));
    }
}
