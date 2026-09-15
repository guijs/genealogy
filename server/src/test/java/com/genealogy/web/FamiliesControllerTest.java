package com.genealogy.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genealogy.domain.person.Person;
import com.genealogy.domain.projection.Gender;
import com.genealogy.domain.projection.ProjectionPerson;
import com.genealogy.store.PersonStore;
import com.genealogy.store.ProjectionStore;
import com.genealogy.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FamiliesControllerTest extends BaseIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String AUTH_HEADER = "Authorization";

    @Autowired
    private PersonStore personStore;

    @Autowired
    private ProjectionStore projectionStore;

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
                        .header(AUTH_HEADER, bearerToken(USER_ID))
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
                        .header(AUTH_HEADER, bearerToken(USER_ID))
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
                        .header(AUTH_HEADER, bearerToken(USER_ID))
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
                        .header(AUTH_HEADER, bearerToken(USER_ID))
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
                        .header(AUTH_HEADER, bearerToken(USER_ID))
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
                        .header(AUTH_HEADER, bearerToken(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        String familyId = new ObjectMapper().readTree(responseBody).get("id").asText();

        mockMvc.perform(get("/api/v1/families/" + familyId)
                        .header(AUTH_HEADER, bearerToken(USER_ID)))
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
                        .header(AUTH_HEADER, bearerToken(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        String familyId = new ObjectMapper().readTree(responseBody).get("id").asText();

        mockMvc.perform(get("/api/v1/families/" + familyId)
                        .header(AUTH_HEADER, bearerToken(OTHER_USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    @Test
    void createFamily_withInvalidJwt_returns401() throws Exception {
        String body = """
            {"name": "Test Family"}
            """;

        mockMvc.perform(post("/api/v1/families")
                        .header(AUTH_HEADER, "Bearer invalid-jwt-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listFamilies_withoutUserIdHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/families"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("authentication required"));
    }

    @Test
    void listFamilies_userWithNoFamilies_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/families")
                        .header(AUTH_HEADER, bearerToken(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.families").isArray())
                .andExpect(jsonPath("$.families").isEmpty());
    }

    @Test
    void listFamilies_userAdminOfAAndMemberOfB_bothListed_otherFamilyCNotListed() throws Exception {
        String familyAName = "Family A";
        String familyBName = "Family B";
        String familyCName = "Family C";

        mockMvc.perform(post("/api/v1/families")
                        .header(AUTH_HEADER, bearerToken(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "%s"}
                            """.formatted(familyAName)))
                .andExpect(status().isCreated());

        MvcResult resultB = mockMvc.perform(post("/api/v1/families")
                        .header(AUTH_HEADER, bearerToken(OTHER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "%s"}
                            """.formatted(familyBName)))
                .andExpect(status().isCreated())
                .andReturn();

        String familyBId = new ObjectMapper().readTree(resultB.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v1/families/" + familyBId + "/members")
                        .header(AUTH_HEADER, bearerToken(OTHER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"user_id": "%s", "role": "viewer"}
                            """.formatted(USER_ID.toString())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/families")
                        .header(AUTH_HEADER, bearerToken(OTHER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "%s"}
                            """.formatted(familyCName)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families")
                        .header(AUTH_HEADER, bearerToken(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.families").isArray())
                .andExpect(jsonPath("$.families.length()").value(2))
                .andExpect(jsonPath("$.families[?(@.name == 'Family A')]").exists())
                .andExpect(jsonPath("$.families[?(@.name == 'Family B')]").exists())
                .andExpect(jsonPath("$.families[?(@.name == 'Family C')]").doesNotExist());
    }

    @Test
    void listFamilies_createFamilyThenList_includesNewFamily() throws Exception {
        String familyName = "New Test Family";

        mockMvc.perform(get("/api/v1/families")
                        .header(AUTH_HEADER, bearerToken(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.families").isEmpty());

        MvcResult createResult = mockMvc.perform(post("/api/v1/families")
                        .header(AUTH_HEADER, bearerToken(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "%s"}
                            """.formatted(familyName)))
                .andExpect(status().isCreated())
                .andReturn();

        String familyId = new ObjectMapper().readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/families")
                        .header(AUTH_HEADER, bearerToken(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.families").isArray())
                .andExpect(jsonPath("$.families.length()").value(1))
                .andExpect(jsonPath("$.families[0].id").value(familyId))
                .andExpect(jsonPath("$.families[0].name").value(familyName));
    }

    @Test
    void listFamilies_withProgenitorSet_includesProgenitorPersonId() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/families")
                        .header(AUTH_HEADER, bearerToken(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Progenitor Test Family\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String familyId = new ObjectMapper().readTree(createResult.getResponse().getContentAsString()).get("id").asText();
        UUID familyUUID = UUID.fromString(familyId);

        UUID personId = UUID.randomUUID();
        personStore.addPerson(new Person(personId, familyUUID, "Test", "Progenitor"));
        projectionStore.createPerson(new ProjectionPerson(personId, familyUUID, "Test Progenitor", Gender.MALE, 1900, null, false));

        mockMvc.perform(put("/api/v1/families/" + familyId + "/progenitor")
                        .header(AUTH_HEADER, bearerToken(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"person_id\": \"" + personId + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/families")
                        .header(AUTH_HEADER, bearerToken(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.families[0].id").value(familyId))
                .andExpect(jsonPath("$.families[0].progenitor_person_id").value(personId.toString()));
    }

    @Test
    void listFamilies_withoutProgenitorSet_progenitorPersonIdIsNull() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/families")
                        .header(AUTH_HEADER, bearerToken(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"No Progenitor Family\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String familyId = new ObjectMapper().readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/families")
                        .header(AUTH_HEADER, bearerToken(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.families[0].id").value(familyId))
                .andExpect(jsonPath("$.families[0].progenitor_person_id").isEmpty());
    }
}
