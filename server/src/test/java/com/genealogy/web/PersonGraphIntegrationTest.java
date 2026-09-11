package com.genealogy.web;

import com.genealogy.domain.family.Role;
import com.genealogy.domain.person.Person;
import com.genealogy.domain.projection.Gender;
import com.genealogy.domain.projection.ProjectionPerson;
import com.genealogy.service.GraphService;
import com.genealogy.service.PersonService;
import com.genealogy.store.FamilyStore;
import com.genealogy.store.PersonStore;
import com.genealogy.store.ProjectionStore;
import com.genealogy.web.filter.AuthFilter;
import com.genealogy.web.filter.FamilyMembershipFilter;
import com.genealogy.web.filter.WriteAccessFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({FamilyController.class, GraphController.class})
@Import({FamilyStore.class, PersonStore.class, ProjectionStore.class,
        PersonService.class, GraphService.class,
        AuthFilter.class, FamilyMembershipFilter.class, WriteAccessFilter.class})
class PersonGraphIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FamilyStore familyStore;

    @Autowired
    private PersonStore personStore;

    @Autowired
    private ProjectionStore projectionStore;

    private static final UUID FAMILY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID VIEWER_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID EXISTING_PERSON_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String FAMILY_NAME = "Test Family";

    @BeforeEach
    void setUp() {
        familyStore.clear();
        personStore.clear();
        projectionStore.clear();

        familyStore.createFamily(FAMILY_ID, FAMILY_NAME);
        familyStore.addMemberWithRole(FAMILY_ID, ADMIN_USER_ID, Role.ADMIN);
        familyStore.addMemberWithRole(FAMILY_ID, VIEWER_USER_ID, Role.VIEWER);

        personStore.addPerson(new Person(EXISTING_PERSON_ID, FAMILY_ID, "Existing", "Person"));
        projectionStore.createPerson(new ProjectionPerson(
                EXISTING_PERSON_ID, FAMILY_ID, "Existing Person", Gender.MALE, 1980, null, false));
    }

    @Test
    void writeThenReadGraph_postPerson_thenGetGraphContainsPerson() throws Exception {
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

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .param("rootPersonId", newPersonId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons", hasSize(1)))
                .andExpect(jsonPath("$.persons[0].id").value(newPersonId))
                .andExpect(jsonPath("$.persons[0].displayName").value("NewRoot Person"));
    }

    @Test
    void writeThenReadGraph_patchPerson_thenGetGraphShowsUpdatedDisplayName() throws Exception {
        String body = """
            {"first_name": "Updated", "last_name": "Name"}
            """;

        mockMvc.perform(patch("/api/v1/families/" + FAMILY_ID + "/persons/" + EXISTING_PERSON_ID)
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .param("rootPersonId", EXISTING_PERSON_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons[0].displayName").value("Updated Name"));
    }

    @Test
    void writeThenReadGraph_viewerCanReadGraphAfterAdminCreates() throws Exception {
        String body = """
            {"first_name": "AdminCreated", "last_name": "Person"}
            """;

        String response = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String newPersonId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response).get("id").asText();

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", VIEWER_USER_ID.toString())
                        .param("rootPersonId", newPersonId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons[0].displayName").value("AdminCreated Person"));
    }

    @Test
    void writeThenReadGraph_viewerPostStillForbidden() throws Exception {
        String body = """
            {"first_name": "Forbidden", "last_name": "Person"}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons")
                        .header("X-User-Id", VIEWER_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("write access required"));
    }

    @Test
    void writeThenReadGraph_listPersonsShowsCreatedPerson() throws Exception {
        String body = """
            {"first_name": "Listed", "last_name": "Person"}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/persons")
                        .header("X-User-Id", ADMIN_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons", hasSize(2)));
    }
}
