package com.genealogy.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genealogy.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FamiliesControllerTest extends BaseIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @BeforeEach
    void setUp() {
        cleanAllData();
    }

    @Test
    void createFamily_withoutUserIdHeader_returns401() throws Exception {
        String body = """
            {"name": "Test Family"}
            """;

        mockMvc.perform(post("/api/v1/families")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("authentication required"));
    }

    @Test
    void createFamily_withBlankName_returns400() throws Exception {
        String body = """
            {"name": "  "}
            """;

        mockMvc.perform(post("/api/v1/families")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("name is required"));
    }

    @Test
    void createFamily_withEmptyName_returns400() throws Exception {
        String body = """
            {"name": ""}
            """;

        mockMvc.perform(post("/api/v1/families")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("name is required"));
    }

    @Test
    void createFamily_withNullName_returns400() throws Exception {
        String body = """
            {}
            """;

        mockMvc.perform(post("/api/v1/families")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("name is required"));
    }

    @Test
    void createFamily_validRequest_returns201WithIdAndName() throws Exception {
        String familyName = "Smith Family";
        String body = """
            {"name": "%s"}
            """.formatted(familyName);

        mockMvc.perform(post("/api/v1/families")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value(familyName));
    }

    @Test
    void createFamily_trimsWhitespaceName_returns201WithTrimmedName() throws Exception {
        String body = """
            {"name": "  Trimmed Family  "}
            """;

        mockMvc.perform(post("/api/v1/families")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Trimmed Family"));
    }

    @Test
    void createFamily_thenGetWithSameUser_returns200() throws Exception {
        String familyName = "Jones Family";
        String body = """
            {"name": "%s"}
            """.formatted(familyName);

        MvcResult createResult = mockMvc.perform(post("/api/v1/families")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        String familyId = new ObjectMapper().readTree(responseBody).get("id").asText();

        mockMvc.perform(get("/api/v1/families/" + familyId)
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(familyId))
                .andExpect(jsonPath("$.name").value(familyName));
    }

    @Test
    void createFamily_thenGetWithDifferentNonMemberUser_returns404() throws Exception {
        String familyName = "Private Family";
        String body = """
            {"name": "%s"}
            """.formatted(familyName);

        MvcResult createResult = mockMvc.perform(post("/api/v1/families")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        String familyId = new ObjectMapper().readTree(responseBody).get("id").asText();

        mockMvc.perform(get("/api/v1/families/" + familyId)
                        .header("X-User-Id", OTHER_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    @Test
    void createFamily_withInvalidUserId_returns401() throws Exception {
        String body = """
            {"name": "Test Family"}
            """;

        mockMvc.perform(post("/api/v1/families")
                        .header("X-User-Id", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid user id"));
    }
}
