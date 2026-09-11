package com.genealogy.web;

import com.genealogy.domain.family.Role;
import com.genealogy.domain.person.Person;
import com.genealogy.store.FamilyStore;
import com.genealogy.store.KinshipStore;
import com.genealogy.store.PersonStore;
import com.genealogy.store.ProjectionStore;
import com.genealogy.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RelationshipGraphIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private FamilyStore familyStore;

    @Autowired
    private PersonStore personStore;

    @Autowired
    private KinshipStore kinshipStore;

    @Autowired
    private ProjectionStore projectionStore;

    private static final UUID FAMILY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID VIEWER_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID NON_MEMBER_USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID PARENT_PERSON_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID CHILD_PERSON_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final String FAMILY_NAME = "Test Family";

    @BeforeEach
    void setUp() {
        cleanAllData();
        kinshipStore.clear();

        familyStore.createFamily(FAMILY_ID, FAMILY_NAME);
        familyStore.addMemberWithRole(FAMILY_ID, ADMIN_USER_ID, Role.ADMIN);
        familyStore.addMemberWithRole(FAMILY_ID, VIEWER_USER_ID, Role.VIEWER);

        personStore.addPerson(new Person(PARENT_PERSON_ID, FAMILY_ID, "John", "Doe"));
        personStore.addPerson(new Person(CHILD_PERSON_ID, FAMILY_ID, "Jane", "Doe"));
    }

    @Test
    void writeThenReadGraph_adminPostRelationship_thenGetGraphContainsBothPersonsAndRelationship() throws Exception {
        String body = """
            {"parent_id": "%s", "child_id": "%s", "relationship_type": "biological_father"}
            """.formatted(PARENT_PERSON_ID, CHILD_PERSON_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .param("rootPersonId", PARENT_PERSON_ID.toString())
                        .param("depth", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.familyId").value(FAMILY_ID.toString()))
                .andExpect(jsonPath("$.rootPersonId").value(PARENT_PERSON_ID.toString()))
                .andExpect(jsonPath("$.persons").isArray())
                .andExpect(jsonPath("$.persons", hasSize(2)))
                .andExpect(jsonPath("$.persons[*].id", hasItem(PARENT_PERSON_ID.toString())))
                .andExpect(jsonPath("$.persons[*].id", hasItem(CHILD_PERSON_ID.toString())))
                .andExpect(jsonPath("$.persons[*].displayName", hasItem("John Doe")))
                .andExpect(jsonPath("$.persons[*].displayName", hasItem("Jane Doe")))
                .andExpect(jsonPath("$.relationships").isArray())
                .andExpect(jsonPath("$.relationships", hasSize(1)))
                .andExpect(jsonPath("$.relationships[0].type").value("PARENT_CHILD"))
                .andExpect(jsonPath("$.relationships[0].subtype").value("biological"))
                .andExpect(jsonPath("$.relationships[0].role").value("father"))
                .andExpect(jsonPath("$.relationships[0].parentId").value(PARENT_PERSON_ID.toString()))
                .andExpect(jsonPath("$.relationships[0].childId").value(CHILD_PERSON_ID.toString()));
    }

    @Test
    void writeThenReadGraph_viewerCanReadGraphAfterAdminWrites() throws Exception {
        String body = """
            {"parent_id": "%s", "child_id": "%s", "relationship_type": "biological_mother"}
            """.formatted(PARENT_PERSON_ID, CHILD_PERSON_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", VIEWER_USER_ID.toString())
                        .param("rootPersonId", PARENT_PERSON_ID.toString())
                        .param("depth", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons", hasSize(2)))
                .andExpect(jsonPath("$.relationships", hasSize(1)))
                .andExpect(jsonPath("$.relationships[0].subtype").value("biological"))
                .andExpect(jsonPath("$.relationships[0].role").value("mother"));
    }

    @Test
    void writeThenReadGraph_viewerPostStillForbidden() throws Exception {
        String body = """
            {"parent_id": "%s", "child_id": "%s", "relationship_type": "biological_father"}
            """.formatted(PARENT_PERSON_ID, CHILD_PERSON_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships")
                        .header("X-User-Id", VIEWER_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("write access required"));
    }

    @Test
    void writeThenReadGraph_nonMemberPostReturns404() throws Exception {
        String body = """
            {"parent_id": "%s", "child_id": "%s", "relationship_type": "biological_father"}
            """.formatted(PARENT_PERSON_ID, CHILD_PERSON_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships")
                        .header("X-User-Id", NON_MEMBER_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    @Test
    void writeThenReadGraph_nonMemberGetReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", NON_MEMBER_USER_ID.toString())
                        .param("rootPersonId", PARENT_PERSON_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    @Test
    void writeThenReadGraph_adoptiveRelationshipMappedCorrectly() throws Exception {
        String body = """
            {"parent_id": "%s", "child_id": "%s", "relationship_type": "adoptive_father"}
            """.formatted(PARENT_PERSON_ID, CHILD_PERSON_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .param("rootPersonId", PARENT_PERSON_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationships[0].subtype").value("adoptive"))
                .andExpect(jsonPath("$.relationships[0].role").value("father"));
    }

    @Test
    void writeThenReadGraph_adoptiveMotherMappedCorrectly() throws Exception {
        String body = """
            {"parent_id": "%s", "child_id": "%s", "relationship_type": "adoptive_mother"}
            """.formatted(PARENT_PERSON_ID, CHILD_PERSON_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .param("rootPersonId", PARENT_PERSON_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationships[0].subtype").value("adoptive"))
                .andExpect(jsonPath("$.relationships[0].role").value("mother"));
    }

    @Test
    void writeThenReadGraph_multipleRelationshipsSync() throws Exception {
        UUID grandchildId = UUID.randomUUID();
        personStore.addPerson(new Person(grandchildId, FAMILY_ID, "Alice", "Doe"));

        String body1 = """
            {"parent_id": "%s", "child_id": "%s", "relationship_type": "biological_father"}
            """.formatted(PARENT_PERSON_ID, CHILD_PERSON_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body1))
                .andExpect(status().isCreated());

        String body2 = """
            {"parent_id": "%s", "child_id": "%s", "relationship_type": "biological_mother"}
            """.formatted(CHILD_PERSON_ID, grandchildId);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .param("rootPersonId", PARENT_PERSON_ID.toString())
                        .param("depth", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons", hasSize(3)))
                .andExpect(jsonPath("$.relationships", hasSize(2)));
    }

    @Test
    void writeThenReadGraph_getGraphFromChildPerspective() throws Exception {
        String body = """
            {"parent_id": "%s", "child_id": "%s", "relationship_type": "biological_father"}
            """.formatted(PARENT_PERSON_ID, CHILD_PERSON_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .param("rootPersonId", CHILD_PERSON_ID.toString())
                        .param("depth", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootPersonId").value(CHILD_PERSON_ID.toString()))
                .andExpect(jsonPath("$.persons", hasSize(2)))
                .andExpect(jsonPath("$.relationships", hasSize(1)));
    }
}
