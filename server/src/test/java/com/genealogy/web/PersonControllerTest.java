package com.genealogy.web;

import com.genealogy.domain.family.Role;
import com.genealogy.domain.person.Person;
import com.genealogy.domain.projection.Gender;
import com.genealogy.domain.projection.ProjectionPerson;
import com.genealogy.store.FamilyStore;
import com.genealogy.store.PersonStore;
import com.genealogy.store.ProjectionStore;
import com.genealogy.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PersonControllerTest extends BaseIntegrationTest {

    @Autowired
    private FamilyStore familyStore;

    @Autowired
    private PersonStore personStore;

    @Autowired
    private ProjectionStore projectionStore;

    private static final UUID FAMILY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID VIEWER_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID NON_MEMBER_USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID EXISTING_PERSON_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String FAMILY_NAME = "Test Family";

    @BeforeEach
    void setUp() {
        cleanAllData();

        familyStore.createFamily(FAMILY_ID, FAMILY_NAME);
        familyStore.addMemberWithRole(FAMILY_ID, ADMIN_USER_ID, Role.ADMIN);
        familyStore.addMemberWithRole(FAMILY_ID, VIEWER_USER_ID, Role.VIEWER);

        personStore.addPerson(new Person(EXISTING_PERSON_ID, FAMILY_ID, "Existing", "Person"));
    }

    // POST /persons - 401 no auth
    @Test
    void createPerson_noAuth_returns401() throws Exception {
        String body = """
            {"first_name": "John", "last_name": "Doe"}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("authentication required"));
    }

    // POST /persons - 404 non-member
    @Test
    void createPerson_nonMember_returns404() throws Exception {
        String body = """
            {"first_name": "John", "last_name": "Doe"}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons")
                        .header("X-User-Id", NON_MEMBER_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    // POST /persons - 403 viewer
    @Test
    void createPerson_viewer_returns403() throws Exception {
        String body = """
            {"first_name": "John", "last_name": "Doe"}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons")
                        .header("X-User-Id", VIEWER_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("write access required"));
    }

    // POST /persons - 201 admin creates + visible in list
    @Test
    void createPerson_admin_returns201AndVisibleInList() throws Exception {
        String body = """
            {"first_name": "John", "last_name": "Doe"}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.first_name").value("John"))
                .andExpect(jsonPath("$.last_name").value("Doe"));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/persons")
                        .header("X-User-Id", ADMIN_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons.length()").value(2));
    }

    // POST /persons then visible in projection store (for graph)
    @Test
    void createPerson_syncsToProjectionStore() throws Exception {
        String body = """
            {"first_name": "NewRoot", "last_name": "Person"}
            """;

        String response = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String newPersonId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response).get("id").asText();

        var projectionPerson = projectionStore.getPerson(UUID.fromString(newPersonId));
        org.assertj.core.api.Assertions.assertThat(projectionPerson).isPresent();
        org.assertj.core.api.Assertions.assertThat(projectionPerson.get().getDisplayName()).isEqualTo("NewRoot Person");
        org.assertj.core.api.Assertions.assertThat(projectionPerson.get().isHidden()).isFalse();
    }

    // PATCH /persons/{id} - 401 no auth
    @Test
    void updatePerson_noAuth_returns401() throws Exception {
        String body = """
            {"first_name": "Updated"}
            """;

        mockMvc.perform(patch("/api/v1/families/" + FAMILY_ID + "/persons/" + EXISTING_PERSON_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("authentication required"));
    }

    // PATCH /persons/{id} - 404 non-member
    @Test
    void updatePerson_nonMember_returns404() throws Exception {
        String body = """
            {"first_name": "Updated"}
            """;

        mockMvc.perform(patch("/api/v1/families/" + FAMILY_ID + "/persons/" + EXISTING_PERSON_ID)
                        .header("X-User-Id", NON_MEMBER_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    // PATCH /persons/{id} - 403 viewer
    @Test
    void updatePerson_viewer_returns403() throws Exception {
        String body = """
            {"first_name": "Updated"}
            """;

        mockMvc.perform(patch("/api/v1/families/" + FAMILY_ID + "/persons/" + EXISTING_PERSON_ID)
                        .header("X-User-Id", VIEWER_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("write access required"));
    }

    // PATCH /persons/{id} - 200 updates list + projection displayName
    @Test
    void updatePerson_admin_returns200AndUpdatesListAndProjection() throws Exception {
        String body = """
            {"first_name": "Updated", "last_name": "Name"}
            """;

        mockMvc.perform(patch("/api/v1/families/" + FAMILY_ID + "/persons/" + EXISTING_PERSON_ID)
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(EXISTING_PERSON_ID.toString()))
                .andExpect(jsonPath("$.first_name").value("Updated"))
                .andExpect(jsonPath("$.last_name").value("Name"));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/persons")
                        .header("X-User-Id", ADMIN_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons[?(@.id=='" + EXISTING_PERSON_ID + "')].first_name").value(hasItem("Updated")))
                .andExpect(jsonPath("$.persons[?(@.id=='" + EXISTING_PERSON_ID + "')].last_name").value(hasItem("Name")));

        var projectionPerson = projectionStore.getPerson(EXISTING_PERSON_ID);
        org.assertj.core.api.Assertions.assertThat(projectionPerson).isPresent();
        org.assertj.core.api.Assertions.assertThat(projectionPerson.get().getDisplayName()).isEqualTo("Updated Name");
    }

    // PATCH /persons/{id} - partial update (only first_name)
    @Test
    void updatePerson_partialUpdate_keepsExistingFields() throws Exception {
        String body = """
            {"first_name": "OnlyFirst"}
            """;

        mockMvc.perform(patch("/api/v1/families/" + FAMILY_ID + "/persons/" + EXISTING_PERSON_ID)
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.first_name").value("OnlyFirst"))
                .andExpect(jsonPath("$.last_name").value("Person"));
    }

    // PATCH /persons/{id} - 404 unknown person
    @Test
    void updatePerson_unknownPerson_returns404() throws Exception {
        UUID unknownPersonId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        String body = """
            {"first_name": "Updated"}
            """;

        mockMvc.perform(patch("/api/v1/families/" + FAMILY_ID + "/persons/" + unknownPersonId)
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    // PATCH /persons/{id} - 404 wrong family person
    @Test
    void updatePerson_wrongFamilyPerson_returns404() throws Exception {
        UUID otherFamilyId = UUID.randomUUID();
        UUID otherPersonId = UUID.randomUUID();
        familyStore.createFamily(otherFamilyId, "Other Family");
        personStore.addPerson(new Person(otherPersonId, otherFamilyId, "Other", "Person"));

        String body = """
            {"first_name": "Updated"}
            """;

        mockMvc.perform(patch("/api/v1/families/" + FAMILY_ID + "/persons/" + otherPersonId)
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    // POST /persons - 400 blank first_name
    @Test
    void createPerson_blankFirstName_returns400() throws Exception {
        String body = """
            {"first_name": "   ", "last_name": "Doe"}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("first_name cannot be blank"));
    }

    // POST /persons - 400 blank last_name
    @Test
    void createPerson_blankLastName_returns400() throws Exception {
        String body = """
            {"first_name": "John", "last_name": "   "}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("last_name cannot be blank"));
    }

    // POST /persons - 400 both names blank/missing
    @Test
    void createPerson_bothNamesBlank_returns400() throws Exception {
        String body = """
            {}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("at least one name field is required"));
    }

    // PATCH /persons/{id} - 400 blank first_name
    @Test
    void updatePerson_blankFirstName_returns400() throws Exception {
        String body = """
            {"first_name": "   "}
            """;

        mockMvc.perform(patch("/api/v1/families/" + FAMILY_ID + "/persons/" + EXISTING_PERSON_ID)
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("first_name cannot be blank"));
    }

    // POST /persons with only first_name - 201
    @Test
    void createPerson_onlyFirstName_returns201() throws Exception {
        String body = """
            {"first_name": "SingleName"}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.first_name").value("SingleName"))
                .andExpect(jsonPath("$.last_name").doesNotExist());
    }

    // POST /persons with only last_name - 201
    @Test
    void createPerson_onlyLastName_returns201() throws Exception {
        String body = """
            {"last_name": "OnlyLast"}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.first_name").doesNotExist())
                .andExpect(jsonPath("$.last_name").value("OnlyLast"));
    }

    // POST /persons - editor can write - 201
    @Test
    void createPerson_editor_returns201() throws Exception {
        UUID editorUserId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        familyStore.addMemberWithRole(FAMILY_ID, editorUserId, Role.EDITOR);

        String body = """
            {"first_name": "Editor", "last_name": "Created"}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons")
                        .header("X-User-Id", editorUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.first_name").value("Editor"))
                .andExpect(jsonPath("$.last_name").value("Created"));
    }

    // POST /persons - invalid familyId format - 404
    @Test
    void createPerson_invalidFamilyIdFormat_returns404() throws Exception {
        String body = """
            {"first_name": "John", "last_name": "Doe"}
            """;

        mockMvc.perform(post("/api/v1/families/not-a-uuid/persons")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    // PATCH /persons/{id} - invalid personId format - 404
    @Test
    void updatePerson_invalidPersonIdFormat_returns404() throws Exception {
        String body = """
            {"first_name": "Updated"}
            """;

        mockMvc.perform(patch("/api/v1/families/" + FAMILY_ID + "/persons/not-a-uuid")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    // Contract check: snake_case PersonResponse
    @Test
    void createPerson_responseUsesSnakeCase() throws Exception {
        String body = """
            {"first_name": "Snake", "last_name": "Case"}
            """;

        String response = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(response).contains("first_name");
        org.assertj.core.api.Assertions.assertThat(response).contains("last_name");
        org.assertj.core.api.Assertions.assertThat(response).doesNotContain("firstName");
        org.assertj.core.api.Assertions.assertThat(response).doesNotContain("lastName");
    }
}
