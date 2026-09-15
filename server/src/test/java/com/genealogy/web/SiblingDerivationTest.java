package com.genealogy.web;

import com.genealogy.domain.family.Role;
import com.genealogy.domain.projection.*;
import com.genealogy.store.FamilyStore;
import com.genealogy.store.ProjectionStore;
import com.genealogy.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AP-R9: Derived sibling read projection tests.
 * Tests sibling derivation based on shared biological parents.
 */
class SiblingDerivationTest extends BaseIntegrationTest {

    private static final String AUTH_HEADER = "Authorization";

    @Autowired
    private FamilyStore familyStore;

    @Autowired
    private ProjectionStore projectionStore;

    private static final UUID FAMILY_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID MEMBER_USER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private static final UUID FATHER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MOTHER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID FATHER2_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID MOTHER2_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID CHILD1_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID CHILD2_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID CHILD3_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID CHILD4_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");

    @BeforeEach
    void setUp() {
        cleanAllData();
        familyStore.createFamily(FAMILY_ID, "Test Family");
        familyStore.addMemberWithRole(FAMILY_ID, MEMBER_USER_ID, Role.ADMIN);
    }

    /**
     * Test 1: Same bio father, different bio mothers → paternal_half siblings.
     */
    @Test
    void sameBioFatherDifferentMothers_paternalHalfSiblings() throws Exception {
        projectionStore.createPerson(new ProjectionPerson(FATHER_ID, FAMILY_ID, "Father", Gender.MALE, 1960, null, false));
        projectionStore.createPerson(new ProjectionPerson(MOTHER_ID, FAMILY_ID, "Mother1", Gender.FEMALE, 1965, null, false));
        projectionStore.createPerson(new ProjectionPerson(MOTHER2_ID, FAMILY_ID, "Mother2", Gender.FEMALE, 1968, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD1_ID, FAMILY_ID, "Child1", Gender.MALE, 1990, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD2_ID, FAMILY_ID, "Child2", Gender.FEMALE, 1995, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, MOTHER_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, MOTHER2_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("rootPersonId", FATHER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siblings").isArray())
                .andExpect(jsonPath("$.siblings.length()").value(1))
                .andExpect(jsonPath("$.siblings[0].kind").value("paternal_half"))
                .andExpect(jsonPath("$.siblings[0].sharedParentIds", hasItem(FATHER_ID.toString())));
    }

    /**
     * Test 2: Same both bio parents → full siblings.
     */
    @Test
    void sameBothBioParents_fullSiblings() throws Exception {
        projectionStore.createPerson(new ProjectionPerson(FATHER_ID, FAMILY_ID, "Father", Gender.MALE, 1960, null, false));
        projectionStore.createPerson(new ProjectionPerson(MOTHER_ID, FAMILY_ID, "Mother", Gender.FEMALE, 1965, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD1_ID, FAMILY_ID, "Child1", Gender.MALE, 1990, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD2_ID, FAMILY_ID, "Child2", Gender.FEMALE, 1992, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, MOTHER_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, MOTHER_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("rootPersonId", FATHER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siblings").isArray())
                .andExpect(jsonPath("$.siblings.length()").value(1))
                .andExpect(jsonPath("$.siblings[0].kind").value("full"))
                .andExpect(jsonPath("$.siblings[0].sharedParentIds", hasSize(2)))
                .andExpect(jsonPath("$.siblings[0].sharedParentIds", hasItem(FATHER_ID.toString())))
                .andExpect(jsonPath("$.siblings[0].sharedParentIds", hasItem(MOTHER_ID.toString())));
    }

    /**
     * Test 3: Only adoptive shared parent → NOT siblings.
     */
    @Test
    void onlyAdoptiveSharedParent_notSiblings() throws Exception {
        projectionStore.createPerson(new ProjectionPerson(FATHER_ID, FAMILY_ID, "Adoptive Father", Gender.MALE, 1960, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD1_ID, FAMILY_ID, "Child1", Gender.MALE, 1990, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD2_ID, FAMILY_ID, "Child2", Gender.FEMALE, 1992, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER_ID, CHILD1_ID,
                ParentChildSubtype.ADOPTIVE, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER_ID, CHILD2_ID,
                ParentChildSubtype.ADOPTIVE, ParentRole.FATHER, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("rootPersonId", FATHER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siblings").isArray())
                .andExpect(jsonPath("$.siblings.length()").value(0));
    }

    /**
     * Test 4: No shared bio parent → empty / not listed.
     */
    @Test
    void noSharedBioParent_notSiblings() throws Exception {
        projectionStore.createPerson(new ProjectionPerson(FATHER_ID, FAMILY_ID, "Father1", Gender.MALE, 1960, null, false));
        projectionStore.createPerson(new ProjectionPerson(FATHER2_ID, FAMILY_ID, "Father2", Gender.MALE, 1962, null, false));
        projectionStore.createPerson(new ProjectionPerson(MOTHER_ID, FAMILY_ID, "Mother1", Gender.FEMALE, 1965, null, false));
        projectionStore.createPerson(new ProjectionPerson(MOTHER2_ID, FAMILY_ID, "Mother2", Gender.FEMALE, 1968, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD1_ID, FAMILY_ID, "Child1", Gender.MALE, 1990, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD2_ID, FAMILY_ID, "Child2", Gender.FEMALE, 1992, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, MOTHER_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER2_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, MOTHER2_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("rootPersonId", FATHER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siblings").isArray())
                .andExpect(jsonPath("$.siblings.length()").value(0));
    }

    /**
     * Test 5: Hidden person not listed as sibling.
     */
    @Test
    void hiddenPersonNotListedAsSibling() throws Exception {
        projectionStore.createPerson(new ProjectionPerson(FATHER_ID, FAMILY_ID, "Father", Gender.MALE, 1960, null, false));
        projectionStore.createPerson(new ProjectionPerson(MOTHER_ID, FAMILY_ID, "Mother", Gender.FEMALE, 1965, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD1_ID, FAMILY_ID, "Child1", Gender.MALE, 1990, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD2_ID, FAMILY_ID, "Child2 (Hidden)", Gender.FEMALE, 1992, null, true));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, MOTHER_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, MOTHER_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("rootPersonId", FATHER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siblings").isArray())
                .andExpect(jsonPath("$.siblings.length()").value(0));
    }

    /**
     * Test 6: Dissolved bio edge not used in derivation.
     */
    @Test
    void dissolvedBioEdgeNotUsed() throws Exception {
        projectionStore.createPerson(new ProjectionPerson(FATHER_ID, FAMILY_ID, "Father", Gender.MALE, 1960, null, false));
        projectionStore.createPerson(new ProjectionPerson(MOTHER_ID, FAMILY_ID, "Mother", Gender.FEMALE, 1965, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD1_ID, FAMILY_ID, "Child1", Gender.MALE, 1990, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD2_ID, FAMILY_ID, "Child2", Gender.FEMALE, 1992, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, MOTHER_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, true));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, MOTHER_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, true));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("rootPersonId", FATHER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siblings").isArray())
                .andExpect(jsonPath("$.siblings.length()").value(0));
    }

    /**
     * Test 7: Same bio mother, different bio fathers → maternal_half siblings.
     */
    @Test
    void sameBioMotherDifferentFathers_maternalHalfSiblings() throws Exception {
        projectionStore.createPerson(new ProjectionPerson(FATHER_ID, FAMILY_ID, "Father1", Gender.MALE, 1960, null, false));
        projectionStore.createPerson(new ProjectionPerson(FATHER2_ID, FAMILY_ID, "Father2", Gender.MALE, 1962, null, false));
        projectionStore.createPerson(new ProjectionPerson(MOTHER_ID, FAMILY_ID, "Mother", Gender.FEMALE, 1965, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD1_ID, FAMILY_ID, "Child1", Gender.MALE, 1990, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD2_ID, FAMILY_ID, "Child2", Gender.FEMALE, 1995, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, MOTHER_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER2_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, MOTHER_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("rootPersonId", MOTHER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siblings").isArray())
                .andExpect(jsonPath("$.siblings.length()").value(1))
                .andExpect(jsonPath("$.siblings[0].kind").value("maternal_half"))
                .andExpect(jsonPath("$.siblings[0].sharedParentIds", hasItem(MOTHER_ID.toString())));
    }

    /**
     * Test 8: Multiple siblings - ensure canonical pair ordering (each pair emitted once).
     */
    @Test
    void multipleSiblings_canonicalPairOrdering() throws Exception {
        projectionStore.createPerson(new ProjectionPerson(FATHER_ID, FAMILY_ID, "Father", Gender.MALE, 1960, null, false));
        projectionStore.createPerson(new ProjectionPerson(MOTHER_ID, FAMILY_ID, "Mother", Gender.FEMALE, 1965, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD1_ID, FAMILY_ID, "Child1", Gender.MALE, 1990, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD2_ID, FAMILY_ID, "Child2", Gender.FEMALE, 1992, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD3_ID, FAMILY_ID, "Child3", Gender.MALE, 1994, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, MOTHER_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, MOTHER_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER_ID, CHILD3_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, MOTHER_ID, CHILD3_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("rootPersonId", FATHER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siblings").isArray())
                .andExpect(jsonPath("$.siblings.length()").value(3))
                .andExpect(jsonPath("$.siblings[*].kind", everyItem(is("full"))));
    }

    /**
     * Test 9: Mixed scenario - bio + adoptive parents - only bio count.
     */
    @Test
    void mixedBioAndAdoptive_onlyBioCount() throws Exception {
        projectionStore.createPerson(new ProjectionPerson(FATHER_ID, FAMILY_ID, "Bio Father", Gender.MALE, 1960, null, false));
        projectionStore.createPerson(new ProjectionPerson(FATHER2_ID, FAMILY_ID, "Adoptive Father", Gender.MALE, 1962, null, false));
        projectionStore.createPerson(new ProjectionPerson(MOTHER_ID, FAMILY_ID, "Bio Mother", Gender.FEMALE, 1965, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD1_ID, FAMILY_ID, "Child1", Gender.MALE, 1990, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD2_ID, FAMILY_ID, "Child2", Gender.FEMALE, 1992, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, MOTHER_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER2_ID, CHILD2_ID,
                ParentChildSubtype.ADOPTIVE, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, MOTHER_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("rootPersonId", MOTHER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siblings").isArray())
                .andExpect(jsonPath("$.siblings.length()").value(1))
                .andExpect(jsonPath("$.siblings[0].kind").value("maternal_half"))
                .andExpect(jsonPath("$.siblings[0].sharedParentIds", hasItem(MOTHER_ID.toString())));
    }

    // ==================== REGRESSION TESTS (MAJOR-1 fix) ====================

    /**
     * Test 10: REGRESSION - Hidden bio father still participates in derivation.
     * Two visible children share a hidden biological father → paternal_half siblings.
     * sharedParentIds should be empty (hidden parent redacted for privacy).
     */
    @Test
    void hiddenBioFather_stillDerivesPaternelHalfSiblings() throws Exception {
        projectionStore.createPerson(new ProjectionPerson(FATHER_ID, FAMILY_ID, "Hidden Father", Gender.MALE, 1960, null, true));
        projectionStore.createPerson(new ProjectionPerson(MOTHER_ID, FAMILY_ID, "Mother1", Gender.FEMALE, 1965, null, false));
        projectionStore.createPerson(new ProjectionPerson(MOTHER2_ID, FAMILY_ID, "Mother2", Gender.FEMALE, 1968, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD1_ID, FAMILY_ID, "Child1", Gender.MALE, 1990, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD2_ID, FAMILY_ID, "Child2", Gender.FEMALE, 1995, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, MOTHER_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, MOTHER2_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("rootPersonId", CHILD1_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siblings").isArray())
                .andExpect(jsonPath("$.siblings.length()").value(1))
                .andExpect(jsonPath("$.siblings[0].kind").value("paternal_half"))
                .andExpect(jsonPath("$.siblings[0].sharedParentIds").isEmpty());
    }

    /**
     * Test 11: REGRESSION - Hidden bio mother still participates in derivation.
     * Two visible children share a hidden biological mother → maternal_half siblings.
     * sharedParentIds should be empty (hidden parent redacted for privacy).
     */
    @Test
    void hiddenBioMother_stillDerivesMaternalHalfSiblings() throws Exception {
        projectionStore.createPerson(new ProjectionPerson(FATHER_ID, FAMILY_ID, "Father1", Gender.MALE, 1960, null, false));
        projectionStore.createPerson(new ProjectionPerson(FATHER2_ID, FAMILY_ID, "Father2", Gender.MALE, 1962, null, false));
        projectionStore.createPerson(new ProjectionPerson(MOTHER_ID, FAMILY_ID, "Hidden Mother", Gender.FEMALE, 1965, null, true));
        projectionStore.createPerson(new ProjectionPerson(CHILD1_ID, FAMILY_ID, "Child1", Gender.MALE, 1990, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD2_ID, FAMILY_ID, "Child2", Gender.FEMALE, 1995, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, MOTHER_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER2_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, MOTHER_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("rootPersonId", CHILD1_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siblings").isArray())
                .andExpect(jsonPath("$.siblings.length()").value(1))
                .andExpect(jsonPath("$.siblings[0].kind").value("maternal_half"))
                .andExpect(jsonPath("$.siblings[0].sharedParentIds").isEmpty());
    }

    /**
     * Test 12: REGRESSION - Hidden bio parents, both shared → full siblings.
     * Two visible children share hidden bio father AND hidden bio mother → full siblings.
     * sharedParentIds should be empty (both hidden parents redacted for privacy).
     */
    @Test
    void hiddenBothBioParents_stillDerivesFullSiblings() throws Exception {
        projectionStore.createPerson(new ProjectionPerson(FATHER_ID, FAMILY_ID, "Hidden Father", Gender.MALE, 1960, null, true));
        projectionStore.createPerson(new ProjectionPerson(MOTHER_ID, FAMILY_ID, "Hidden Mother", Gender.FEMALE, 1965, null, true));
        projectionStore.createPerson(new ProjectionPerson(CHILD1_ID, FAMILY_ID, "Child1", Gender.MALE, 1990, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD2_ID, FAMILY_ID, "Child2", Gender.FEMALE, 1992, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, MOTHER_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, MOTHER_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("rootPersonId", CHILD1_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siblings").isArray())
                .andExpect(jsonPath("$.siblings.length()").value(1))
                .andExpect(jsonPath("$.siblings[0].kind").value("full"))
                .andExpect(jsonPath("$.siblings[0].sharedParentIds").isEmpty());
    }

    /**
     * Test 13: One shared bio parent known, other side unknown → half siblings.
     * Child1 has father + mother, Child2 only has same father → paternal_half.
     */
    @Test
    void oneSharedParent_otherUnknown_halfSiblings() throws Exception {
        projectionStore.createPerson(new ProjectionPerson(FATHER_ID, FAMILY_ID, "Father", Gender.MALE, 1960, null, false));
        projectionStore.createPerson(new ProjectionPerson(MOTHER_ID, FAMILY_ID, "Mother", Gender.FEMALE, 1965, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD1_ID, FAMILY_ID, "Child1", Gender.MALE, 1990, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD2_ID, FAMILY_ID, "Child2", Gender.FEMALE, 1995, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, MOTHER_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("rootPersonId", FATHER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siblings").isArray())
                .andExpect(jsonPath("$.siblings.length()").value(1))
                .andExpect(jsonPath("$.siblings[0].kind").value("paternal_half"))
                .andExpect(jsonPath("$.siblings[0].sharedParentIds", hasItem(FATHER_ID.toString())));
    }

    /**
     * Test 14: Stronger canonical pair ordering assertion.
     * Ensure personId < siblingId lexicographically for all pairs.
     */
    @Test
    void canonicalPairOrdering_personIdLessThanSiblingId() throws Exception {
        projectionStore.createPerson(new ProjectionPerson(FATHER_ID, FAMILY_ID, "Father", Gender.MALE, 1960, null, false));
        projectionStore.createPerson(new ProjectionPerson(MOTHER_ID, FAMILY_ID, "Mother", Gender.FEMALE, 1965, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD1_ID, FAMILY_ID, "Child1", Gender.MALE, 1990, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD2_ID, FAMILY_ID, "Child2", Gender.FEMALE, 1992, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD3_ID, FAMILY_ID, "Child3", Gender.MALE, 1994, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, MOTHER_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, MOTHER_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER_ID, CHILD3_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, MOTHER_ID, CHILD3_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        String response = mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("rootPersonId", FATHER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siblings").isArray())
                .andExpect(jsonPath("$.siblings.length()").value(3))
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response);
        com.fasterxml.jackson.databind.JsonNode siblings = root.get("siblings");

        for (com.fasterxml.jackson.databind.JsonNode sibling : siblings) {
            String personId = sibling.get("personId").asText();
            String siblingId = sibling.get("siblingId").asText();
            org.assertj.core.api.Assertions.assertThat(personId.compareTo(siblingId))
                    .as("personId (%s) should be lexicographically less than siblingId (%s)", personId, siblingId)
                    .isLessThan(0);
        }
    }

    /**
     * Test 15: Mixed visible and hidden parents - only visible in sharedParentIds.
     * Father is visible, mother is hidden → paternal_half (if share only father)
     * or full (if share both) with only father in sharedParentIds.
     */
    @Test
    void mixedVisibleAndHiddenParents_onlyVisibleInSharedParentIds() throws Exception {
        projectionStore.createPerson(new ProjectionPerson(FATHER_ID, FAMILY_ID, "Visible Father", Gender.MALE, 1960, null, false));
        projectionStore.createPerson(new ProjectionPerson(MOTHER_ID, FAMILY_ID, "Hidden Mother", Gender.FEMALE, 1965, null, true));
        projectionStore.createPerson(new ProjectionPerson(CHILD1_ID, FAMILY_ID, "Child1", Gender.MALE, 1990, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD2_ID, FAMILY_ID, "Child2", Gender.FEMALE, 1992, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, MOTHER_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, FATHER_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, MOTHER_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("rootPersonId", CHILD1_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siblings").isArray())
                .andExpect(jsonPath("$.siblings.length()").value(1))
                .andExpect(jsonPath("$.siblings[0].kind").value("full"))
                .andExpect(jsonPath("$.siblings[0].sharedParentIds", hasSize(1)))
                .andExpect(jsonPath("$.siblings[0].sharedParentIds", hasItem(FATHER_ID.toString())))
                .andExpect(jsonPath("$.siblings[0].sharedParentIds", not(hasItem(MOTHER_ID.toString()))));
    }
}
