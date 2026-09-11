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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RelationshipControllerTest extends BaseIntegrationTest {

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
    private static final UUID PERSON1_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID PERSON2_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID PERSON3_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID PERSON4_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID PERSON_NOT_IN_FAMILY_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final String FAMILY_NAME = "Test Family";

    @BeforeEach
    void setUp() {
        cleanAllData();
        kinshipStore.clear();

        familyStore.createFamily(FAMILY_ID, FAMILY_NAME);
        familyStore.addMemberWithRole(FAMILY_ID, ADMIN_USER_ID, Role.ADMIN);
        familyStore.addMemberWithRole(FAMILY_ID, VIEWER_USER_ID, Role.VIEWER);

        personStore.addPerson(new Person(PERSON1_ID, FAMILY_ID, "John", "Doe"));
        personStore.addPerson(new Person(PERSON2_ID, FAMILY_ID, "Jane", "Doe"));
        personStore.addPerson(new Person(PERSON3_ID, FAMILY_ID, "Alice", "Doe"));
        personStore.addPerson(new Person(PERSON4_ID, FAMILY_ID, "Bob", "Doe"));
    }

    // Test 1: viewer POST → 403 (B3 write gate)
    @Test
    void addRelationship_viewerUser_returns403() throws Exception {
        String body = """
            {"parent_id": "%s", "child_id": "%s", "relationship_type": "biological_father"}
            """.formatted(PERSON1_ID, PERSON2_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships")
                        .header("X-User-Id", VIEWER_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("write access required"));
    }

    // Test 2: non-member POST → 404 (B1)
    @Test
    void addRelationship_nonMemberUser_returns404() throws Exception {
        String body = """
            {"parent_id": "%s", "child_id": "%s", "relationship_type": "biological_father"}
            """.formatted(PERSON1_ID, PERSON2_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships")
                        .header("X-User-Id", NON_MEMBER_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    // Test 3: no auth → 401
    @Test
    void addRelationship_noAuth_returns401() throws Exception {
        String body = """
            {"parent_id": "%s", "child_id": "%s", "relationship_type": "biological_father"}
            """.formatted(PERSON1_ID, PERSON2_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("authentication required"));
    }

    // Test 4: admin valid → 201
    @Test
    void addRelationship_adminValid_returns201() throws Exception {
        String body = """
            {"parent_id": "%s", "child_id": "%s", "relationship_type": "biological_father"}
            """.formatted(PERSON1_ID, PERSON2_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    // Test 5: self-loop → 422
    @Test
    void addRelationship_selfLoop_returns422() throws Exception {
        String body = """
            {"parent_id": "%s", "child_id": "%s", "relationship_type": "biological_father"}
            """.formatted(PERSON1_ID, PERSON1_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("person cannot be their own parent"));
    }

    // Test 6: dual biological father → 422 (K1)
    @Test
    void addRelationship_dualBiologicalFather_returns422() throws Exception {
        String firstFather = """
            {"parent_id": "%s", "child_id": "%s", "relationship_type": "biological_father"}
            """.formatted(PERSON1_ID, PERSON3_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstFather))
                .andExpect(status().isCreated());

        String secondFather = """
            {"parent_id": "%s", "child_id": "%s", "relationship_type": "biological_father"}
            """.formatted(PERSON2_ID, PERSON3_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondFather))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("kinship: person already has a biological father"));
    }

    // Test 7: dual adoptive mother → 422 (K1)
    @Test
    void addRelationship_dualAdoptiveMother_returns422() throws Exception {
        String firstMother = """
            {"parent_id": "%s", "child_id": "%s", "relationship_type": "adoptive_mother"}
            """.formatted(PERSON1_ID, PERSON3_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstMother))
                .andExpect(status().isCreated());

        String secondMother = """
            {"parent_id": "%s", "child_id": "%s", "relationship_type": "adoptive_mother"}
            """.formatted(PERSON2_ID, PERSON3_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondMother))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("kinship: person already has an adoptive mother"));
    }

    // Test 8: cycle → 422
    @Test
    void addRelationship_cycle_returns422() throws Exception {
        String parent1ToChild = """
            {"parent_id": "%s", "child_id": "%s", "relationship_type": "biological_father"}
            """.formatted(PERSON1_ID, PERSON2_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parent1ToChild))
                .andExpect(status().isCreated());

        String childToParent = """
            {"parent_id": "%s", "child_id": "%s", "relationship_type": "biological_mother"}
            """.formatted(PERSON2_ID, PERSON1_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(childToParent))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("relationship would create a cycle"));
    }

    // Test 9: bio+adoptive father OK → 201 (different parent types may coexist)
    @Test
    void addRelationship_bioAndAdoptiveFather_returns201() throws Exception {
        String bioFather = """
            {"parent_id": "%s", "child_id": "%s", "relationship_type": "biological_father"}
            """.formatted(PERSON1_ID, PERSON3_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bioFather))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        String adoptiveFather = """
            {"parent_id": "%s", "child_id": "%s", "relationship_type": "adoptive_father"}
            """.formatted(PERSON2_ID, PERSON3_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adoptiveFather))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    // Test 10: person not in family → 404
    @Test
    void addRelationship_personNotInFamily_returns404() throws Exception {
        String body = """
            {"parent_id": "%s", "child_id": "%s", "relationship_type": "biological_father"}
            """.formatted(PERSON_NOT_IN_FAMILY_ID, PERSON2_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    // Additional test: invalid relationship_type → 400
    @Test
    void addRelationship_invalidRelationshipType_returns400() throws Exception {
        String body = """
            {"parent_id": "%s", "child_id": "%s", "relationship_type": "invalid_type"}
            """.formatted(PERSON1_ID, PERSON2_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid relationship_type"));
    }

    // Additional test: invalid parent_id UUID → 400
    @Test
    void addRelationship_invalidParentIdUuid_returns400() throws Exception {
        String body = """
            {"parent_id": "not-a-uuid", "child_id": "%s", "relationship_type": "biological_father"}
            """.formatted(PERSON2_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid parent_id"));
    }

    // Additional test: editor can write → 201
    @Test
    void addRelationship_editorUser_returns201() throws Exception {
        UUID editorUserId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        familyStore.addMemberWithRole(FAMILY_ID, editorUserId, Role.EDITOR);

        String body = """
            {"parent_id": "%s", "child_id": "%s", "relationship_type": "biological_father"}
            """.formatted(PERSON1_ID, PERSON2_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships")
                        .header("X-User-Id", editorUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }
}
