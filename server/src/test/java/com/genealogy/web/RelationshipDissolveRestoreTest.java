package com.genealogy.web;

import com.genealogy.domain.family.Role;
import com.genealogy.domain.person.Person;
import com.genealogy.domain.projection.*;
import com.genealogy.store.*;
import com.genealogy.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AP-R14: Relationship dissolve/restore tests.
 */
class RelationshipDissolveRestoreTest extends BaseIntegrationTest {

    private static final String AUTH_HEADER = "Authorization";

    @Autowired
    private FamilyStore familyStore;

    @Autowired
    private PersonStore personStore;

    @Autowired
    private ProjectionStore projectionStore;

    @Autowired
    private KinshipStore kinshipStore;

    private static final UUID FAMILY_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ADMIN_USER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID EDITOR_USER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID VIEWER_USER_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID NON_MEMBER_USER_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    private static final UUID FATHER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FATHER2_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID MOTHER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CHILD_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID CHILD2_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @BeforeEach
    void setUp() {
        cleanAllData();
        kinshipStore.clear();

        familyStore.createFamily(FAMILY_ID, "Test Family");
        familyStore.addMemberWithRole(FAMILY_ID, ADMIN_USER_ID, Role.ADMIN);
        familyStore.addMemberWithRole(FAMILY_ID, EDITOR_USER_ID, Role.EDITOR);
        familyStore.addMemberWithRole(FAMILY_ID, VIEWER_USER_ID, Role.VIEWER);

        personStore.addPerson(new Person(FATHER_ID, FAMILY_ID, "Father", "One"));
        personStore.addPerson(new Person(FATHER2_ID, FAMILY_ID, "Father", "Two"));
        personStore.addPerson(new Person(MOTHER_ID, FAMILY_ID, "Mother", "One"));
        personStore.addPerson(new Person(CHILD_ID, FAMILY_ID, "Child", "One"));
        personStore.addPerson(new Person(CHILD2_ID, FAMILY_ID, "Child", "Two"));

        projectionStore.createPerson(new ProjectionPerson(FATHER_ID, FAMILY_ID, "Father One", Gender.MALE, 1960, null, false));
        projectionStore.createPerson(new ProjectionPerson(FATHER2_ID, FAMILY_ID, "Father Two", Gender.MALE, 1962, null, false));
        projectionStore.createPerson(new ProjectionPerson(MOTHER_ID, FAMILY_ID, "Mother One", Gender.FEMALE, 1965, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD_ID, FAMILY_ID, "Child One", Gender.MALE, 1990, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD2_ID, FAMILY_ID, "Child Two", Gender.FEMALE, 1992, null, false));
    }

    /**
     * Test 1: dissolve bio edge → graph no longer shows it; siblings derived from that parent drop.
     */
    @Test
    void dissolveBioEdge_graphNoLongerShowsIt_siblingsDrop() throws Exception {
        UUID relFatherChild1 = UUID.randomUUID();
        UUID relMotherChild1 = UUID.randomUUID();
        UUID relFatherChild2 = UUID.randomUUID();
        UUID relMotherChild2 = UUID.randomUUID();

        projectionStore.createRelationship(new ProjectionRelationship(
                relFatherChild1, FAMILY_ID, FATHER_ID, CHILD_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                relMotherChild1, FAMILY_ID, MOTHER_ID, CHILD_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                relFatherChild2, FAMILY_ID, FATHER_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                relMotherChild2, FAMILY_ID, MOTHER_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        kinshipStore.invalidateCache(FAMILY_ID);

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .param("rootPersonId", FATHER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationships", hasSize(4)))
                .andExpect(jsonPath("$.siblings", hasSize(1)))
                .andExpect(jsonPath("$.siblings[0].kind").value("full"));

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships/" + relFatherChild1 + "/dissolve")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(relFatherChild1.toString()))
                .andExpect(jsonPath("$.dissolved").value(true));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .param("rootPersonId", FATHER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationships", hasSize(3)))
                .andExpect(jsonPath("$.siblings", hasSize(1)))
                .andExpect(jsonPath("$.siblings[0].kind").value("maternal_half"));
    }

    /**
     * Test 2: restore → graph shows again; siblings return.
     */
    @Test
    void restore_graphShowsAgain_siblingsReturn() throws Exception {
        UUID relFatherChild1 = UUID.randomUUID();
        UUID relMotherChild1 = UUID.randomUUID();
        UUID relFatherChild2 = UUID.randomUUID();
        UUID relMotherChild2 = UUID.randomUUID();

        projectionStore.createRelationship(new ProjectionRelationship(
                relFatherChild1, FAMILY_ID, FATHER_ID, CHILD_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, true));
        projectionStore.createRelationship(new ProjectionRelationship(
                relMotherChild1, FAMILY_ID, MOTHER_ID, CHILD_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                relFatherChild2, FAMILY_ID, FATHER_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                relMotherChild2, FAMILY_ID, MOTHER_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        kinshipStore.invalidateCache(FAMILY_ID);

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .param("rootPersonId", FATHER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationships", hasSize(3)))
                .andExpect(jsonPath("$.siblings", hasSize(1)))
                .andExpect(jsonPath("$.siblings[0].kind").value("maternal_half"));

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships/" + relFatherChild1 + "/restore")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(relFatherChild1.toString()))
                .andExpect(jsonPath("$.dissolved").value(false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .param("rootPersonId", FATHER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationships", hasSize(4)))
                .andExpect(jsonPath("$.siblings", hasSize(1)))
                .andExpect(jsonPath("$.siblings[0].kind").value("full"));
    }

    /**
     * Test 3: dissolve then add second bio father succeeds (cardinality freed).
     */
    @Test
    void dissolve_thenAddSecondBioFather_succeeds() throws Exception {
        UUID relFatherChild = UUID.randomUUID();
        projectionStore.createRelationship(new ProjectionRelationship(
                relFatherChild, FAMILY_ID, FATHER_ID, CHILD_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));

        kinshipStore.invalidateCache(FAMILY_ID);

        String addSecondFather = """
            {"parent_id": "%s", "child_id": "%s", "relationship_type": "biological_father"}
            """.formatted(FATHER2_ID, CHILD_ID);
        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addSecondFather))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error").value("kinship: person already has a biological father"));

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships/" + relFatherChild + "/dissolve")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addSecondFather))
                .andExpect(status().isCreated());
    }

    /**
     * Test 4: restore blocked if second bio father already exists → 422, stays dissolved.
     */
    @Test
    void restoreBlocked_ifSecondBioFatherExists_stays422AndDissolved() throws Exception {
        UUID relFatherChild = UUID.randomUUID();
        projectionStore.createRelationship(new ProjectionRelationship(
                relFatherChild, FAMILY_ID, FATHER_ID, CHILD_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, true));

        UUID relFather2Child = UUID.randomUUID();
        projectionStore.createRelationship(new ProjectionRelationship(
                relFather2Child, FAMILY_ID, FATHER2_ID, CHILD_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));

        kinshipStore.invalidateCache(FAMILY_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships/" + relFatherChild + "/restore")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error").value(containsString("biological father")));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .param("rootPersonId", CHILD_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationships", hasSize(1)))
                .andExpect(jsonPath("$.relationships[0].parentId").value(FATHER2_ID.toString()));
    }

    /**
     * Test 5a: 401 missing auth.
     */
    @Test
    void dissolve_missingAuth_returns401() throws Exception {
        UUID relId = UUID.randomUUID();
        projectionStore.createRelationship(new ProjectionRelationship(
                relId, FAMILY_ID, FATHER_ID, CHILD_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships/" + relId + "/dissolve")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test 5b: non-member 404.
     */
    @Test
    void dissolve_nonMember_returns404() throws Exception {
        UUID relId = UUID.randomUUID();
        projectionStore.createRelationship(new ProjectionRelationship(
                relId, FAMILY_ID, FATHER_ID, CHILD_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships/" + relId + "/dissolve")
                        .header(AUTH_HEADER, bearerToken(NON_MEMBER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    /**
     * Test 5c: viewer 403.
     */
    @Test
    void dissolve_viewer_returns403() throws Exception {
        UUID relId = UUID.randomUUID();
        projectionStore.createRelationship(new ProjectionRelationship(
                relId, FAMILY_ID, FATHER_ID, CHILD_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships/" + relId + "/dissolve")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    /**
     * Test 5d: editor OK.
     */
    @Test
    void dissolve_editor_succeeds() throws Exception {
        UUID relId = UUID.randomUUID();
        projectionStore.createRelationship(new ProjectionRelationship(
                relId, FAMILY_ID, FATHER_ID, CHILD_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));

        kinshipStore.invalidateCache(FAMILY_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships/" + relId + "/dissolve")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dissolved").value(true));
    }

    /**
     * Test 5e: restore auth tests (401, 404, 403).
     */
    @Test
    void restore_missingAuth_returns401() throws Exception {
        UUID relId = UUID.randomUUID();
        projectionStore.createRelationship(new ProjectionRelationship(
                relId, FAMILY_ID, FATHER_ID, CHILD_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, true));

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships/" + relId + "/restore")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void restore_nonMember_returns404() throws Exception {
        UUID relId = UUID.randomUUID();
        projectionStore.createRelationship(new ProjectionRelationship(
                relId, FAMILY_ID, FATHER_ID, CHILD_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, true));

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships/" + relId + "/restore")
                        .header(AUTH_HEADER, bearerToken(NON_MEMBER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void restore_viewer_returns403() throws Exception {
        UUID relId = UUID.randomUUID();
        projectionStore.createRelationship(new ProjectionRelationship(
                relId, FAMILY_ID, FATHER_ID, CHILD_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, true));

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships/" + relId + "/restore")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    /**
     * Test 6: dissolve adoptive works similarly.
     */
    @Test
    void dissolveAdoptive_works() throws Exception {
        UUID relId = UUID.randomUUID();
        projectionStore.createRelationship(new ProjectionRelationship(
                relId, FAMILY_ID, FATHER_ID, CHILD_ID,
                ParentChildSubtype.ADOPTIVE, ParentRole.FATHER, null, false));

        kinshipStore.invalidateCache(FAMILY_ID);

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .param("rootPersonId", CHILD_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationships", hasSize(1)));

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships/" + relId + "/dissolve")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dissolved").value(true));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .param("rootPersonId", CHILD_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationships", hasSize(0)));
    }

    /**
     * Test: dissolve already dissolved → 409.
     */
    @Test
    void dissolve_alreadyDissolved_returns409() throws Exception {
        UUID relId = UUID.randomUUID();
        projectionStore.createRelationship(new ProjectionRelationship(
                relId, FAMILY_ID, FATHER_ID, CHILD_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, true));

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships/" + relId + "/dissolve")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is(409))
                .andExpect(jsonPath("$.error").value("relationship is already dissolved"));
    }

    /**
     * Test: restore not dissolved → 409.
     */
    @Test
    void restore_notDissolved_returns409() throws Exception {
        UUID relId = UUID.randomUUID();
        projectionStore.createRelationship(new ProjectionRelationship(
                relId, FAMILY_ID, FATHER_ID, CHILD_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));

        kinshipStore.invalidateCache(FAMILY_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships/" + relId + "/restore")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is(409))
                .andExpect(jsonPath("$.error").value("relationship is not dissolved"));
    }

    /**
     * Test: dissolve relationship not in family → 404.
     */
    @Test
    void dissolve_relationshipNotInFamily_returns404() throws Exception {
        UUID otherFamilyId = UUID.randomUUID();
        familyStore.createFamily(otherFamilyId, "Other Family");

        UUID relId = UUID.randomUUID();
        projectionStore.createRelationship(new ProjectionRelationship(
                relId, otherFamilyId, FATHER_ID, CHILD_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships/" + relId + "/dissolve")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    /**
     * Test: invalid UUID returns 404.
     */
    @Test
    void dissolve_invalidUUID_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships/not-a-uuid/dissolve")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    /**
     * Test: restore blocked by cycle detection.
     */
    @Test
    void restoreBlocked_byCycleDetection_returns422() throws Exception {
        UUID grandparentId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        personStore.addPerson(new Person(grandparentId, FAMILY_ID, "Grandparent", "One"));
        projectionStore.createPerson(new ProjectionPerson(grandparentId, FAMILY_ID, "Grandparent One", Gender.MALE, 1940, null, false));

        UUID relGrandparentFather = UUID.randomUUID();
        projectionStore.createRelationship(new ProjectionRelationship(
                relGrandparentFather, FAMILY_ID, grandparentId, FATHER_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));

        UUID relFatherChild = UUID.randomUUID();
        projectionStore.createRelationship(new ProjectionRelationship(
                relFatherChild, FAMILY_ID, FATHER_ID, CHILD_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));

        UUID relChildGrandparent = UUID.randomUUID();
        projectionStore.createRelationship(new ProjectionRelationship(
                relChildGrandparent, FAMILY_ID, CHILD_ID, grandparentId,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, true));

        kinshipStore.invalidateCache(FAMILY_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships/" + relChildGrandparent + "/restore")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error").value(containsString("cycle")));
    }

    /**
     * Test: restore blocked by self-loop would be caught but is practically impossible
     * since we validate on creation. Testing for completeness.
     */
    @Test
    void restoreBlocked_bySelfLoop_returns422() throws Exception {
        UUID relSelfLoop = UUID.randomUUID();
        projectionStore.createRelationship(new ProjectionRelationship(
                relSelfLoop, FAMILY_ID, FATHER_ID, FATHER_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, true));

        kinshipStore.invalidateCache(FAMILY_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/relationships/" + relSelfLoop + "/restore")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error").value(containsString("own parent")));
    }
}
