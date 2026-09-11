package com.genealogy.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genealogy.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest extends BaseIntegrationTest {

    private static final String AUTH_HEADER = "Authorization";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        cleanAllData();
    }

    // Register tests

    @Test
    void register_validRequest_returns201WithUserIdAndToken() throws Exception {
        String body = """
            {"email": "test@example.com", "password": "password123"}
            """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user_id").exists())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        String body = """
            {"email": "duplicate@example.com", "password": "password123"}
            """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("email already registered"));
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        String body = """
            {"email": "not-an-email", "password": "password123"}
            """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid email format"));
    }

    @Test
    void register_missingEmail_returns400() throws Exception {
        String body = """
            {"password": "password123"}
            """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("email is required"));
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        String body = """
            {"email": "test@example.com", "password": "short"}
            """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("password must be at least 8 characters"));
    }

    @Test
    void register_missingPassword_returns400() throws Exception {
        String body = """
            {"email": "test@example.com"}
            """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("password must be at least 8 characters"));
    }

    // Login tests

    @Test
    void login_validCredentials_returns200WithUserIdAndToken() throws Exception {
        String registerBody = """
            {"email": "login@example.com", "password": "password123"}
            """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated());

        String loginBody = """
            {"email": "login@example.com", "password": "password123"}
            """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").exists())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        String registerBody = """
            {"email": "wrongpass@example.com", "password": "correctpassword"}
            """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated());

        String loginBody = """
            {"email": "wrongpass@example.com", "password": "wrongpassword"}
            """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid credentials"));
    }

    @Test
    void login_nonExistentUser_returns401() throws Exception {
        String body = """
            {"email": "nonexistent@example.com", "password": "password123"}
            """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid credentials"));
    }

    @Test
    void login_missingEmail_returns400() throws Exception {
        String body = """
            {"password": "password123"}
            """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("email is required"));
    }

    @Test
    void login_missingPassword_returns400() throws Exception {
        String body = """
            {"email": "test@example.com"}
            """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("password is required"));
    }

    // Protected endpoint tests

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/families"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("authentication required"));
    }

    @Test
    void protectedEndpoint_withValidToken_returns200() throws Exception {
        String registerBody = """
            {"email": "protected@example.com", "password": "password123"}
            """;

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = responseJson.get("token").asText();

        mockMvc.perform(get("/api/v1/families")
                        .header(AUTH_HEADER, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpoint_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/families")
                        .header(AUTH_HEADER, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withExpiredToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/families")
                        .header(AUTH_HEADER, "Bearer eyJhbGciOiJIUzI1NiJ9.eyJ1c2VyX2lkIjoiMTIzNDU2NzgtMTIzNC0xMjM0LTEyMzQtMTIzNDU2Nzg5MDEyIiwiaWF0IjoxNjA5NDU5MjAwLCJleHAiOjE2MDk0NTkyMDF9.invalid"))
                .andExpect(status().isUnauthorized());
    }

    // Integration: register → create family → verify admin membership

    @Test
    void registerThenCreateFamily_createsAdminMembership() throws Exception {
        String registerBody = """
            {"email": "family@example.com", "password": "password123"}
            """;

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode registerJson = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        String token = registerJson.get("token").asText();
        String userId = registerJson.get("user_id").asText();

        String createFamilyBody = """
            {"name": "JWT Test Family"}
            """;

        MvcResult familyResult = mockMvc.perform(post("/api/v1/families")
                        .header(AUTH_HEADER, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createFamilyBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("JWT Test Family"))
                .andReturn();

        JsonNode familyJson = objectMapper.readTree(familyResult.getResponse().getContentAsString());
        String familyId = familyJson.get("id").asText();

        mockMvc.perform(get("/api/v1/families/" + familyId + "/members")
                        .header(AUTH_HEADER, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members").isArray())
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].user_id").value(userId))
                .andExpect(jsonPath("$.members[0].role").value("admin"));
    }

    // Health endpoint should be public

    @Test
    void healthEndpoint_noAuth_returns200() throws Exception {
        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk());
    }

    // Auth endpoints should be public

    @Test
    void authEndpoints_noToken_accessible() throws Exception {
        String body = """
            {"email": "public@example.com", "password": "password123"}
            """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }
}
