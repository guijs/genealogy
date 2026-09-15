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

class GenerationsControllerTest extends BaseIntegrationTest {

    private static final String AUTH_HEADER = "Authorization";

    @Autowired
    private FamilyStore familyStore;

    @Autowired
    private ProjectionStore projectionStore;

    private static final UUID FAMILY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEMBER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID NON_MEMBER_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID EGO_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final String FAMILY_NAME = "Test Family";

    @BeforeEach
    void setUp() {
        cleanAllData();

        familyStore.createFamily(FAMILY_ID, FAMILY_NAME);
        familyStore.addMemberWithRole(FAMILY_ID, MEMBER_USER_ID, Role.ADMIN);

        projectionStore.createPerson(new ProjectionPerson(
                EGO_ID, FAMILY_ID, "Ego Person", Gender.MALE, 1990, null, false));
    }

    @Test
    void getGenerations_withoutUserIdHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/generations")
                        .param("focusPersonId", EGO_ID.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("authentication required"));
    }

    @Test
    void getGenerations_withNonMemberUser_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/generations")
                        .header(AUTH_HEADER, bearerToken(NON_MEMBER_USER_ID))
                        .param("focusPersonId", EGO_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    @Test
    void getGenerations_withoutFocusPersonId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/generations")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("focusPersonId query parameter required"));
    }

    @Test
    void getGenerations_withInvalidFocusPersonId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/generations")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("focusPersonId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid focusPersonId"));
    }

    @Test
    void getGenerations_focusNotInFamily_returns404() throws Exception {
        UUID otherFamilyId = UUID.randomUUID();
        UUID otherPersonId = UUID.randomUUID();

        familyStore.createFamily(otherFamilyId, "Other Family");
        familyStore.addMemberWithRole(otherFamilyId, MEMBER_USER_ID, Role.ADMIN);

        projectionStore.createPerson(new ProjectionPerson(
                otherPersonId, otherFamilyId, "Other Person", Gender.MALE, 1980, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/generations")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("focusPersonId", otherPersonId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    @Test
    void getGenerations_focusDoesNotExist_returns404() throws Exception {
        UUID nonExistentPersonId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/generations")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("focusPersonId", nonExistentPersonId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    @Test
    void getGenerations_egoAlone_onlyIndex0() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/generations")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("focusPersonId", EGO_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.familyId").value(FAMILY_ID.toString()))
                .andExpect(jsonPath("$.focusPersonId").value(EGO_ID.toString()))
                .andExpect(jsonPath("$.generations").isArray())
                .andExpect(jsonPath("$.generations.length()").value(1))
                .andExpect(jsonPath("$.generations[0].index").value(0))
                .andExpect(jsonPath("$.generations[0].persons.length()").value(1))
                .andExpect(jsonPath("$.generations[0].persons[0].id").value(EGO_ID.toString()))
                .andExpect(jsonPath("$.generations[0].persons[0].displayName").value("Ego Person"))
                .andExpect(jsonPath("$.generations[0].persons[0].conflict").doesNotExist());
    }

    @Test
    void getGenerations_bioParents_atIndexMinus1() throws Exception {
        UUID fatherId = UUID.randomUUID();
        UUID motherId = UUID.randomUUID();

        projectionStore.createPerson(new ProjectionPerson(
                fatherId, FAMILY_ID, "Father", Gender.MALE, 1960, null, false));
        projectionStore.createPerson(new ProjectionPerson(
                motherId, FAMILY_ID, "Mother", Gender.FEMALE, 1962, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, fatherId, EGO_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, motherId, EGO_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/generations")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("focusPersonId", EGO_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generations.length()").value(2))
                .andExpect(jsonPath("$.generations[0].index").value(-1))
                .andExpect(jsonPath("$.generations[0].persons.length()").value(2))
                .andExpect(jsonPath("$.generations[0].persons[*].id", hasItem(fatherId.toString())))
                .andExpect(jsonPath("$.generations[0].persons[*].id", hasItem(motherId.toString())))
                .andExpect(jsonPath("$.generations[1].index").value(0))
                .andExpect(jsonPath("$.generations[1].persons[0].id").value(EGO_ID.toString()));
    }

    @Test
    void getGenerations_bioChildren_atIndexPlus1() throws Exception {
        UUID childId = UUID.randomUUID();

        projectionStore.createPerson(new ProjectionPerson(
                childId, FAMILY_ID, "Child", Gender.FEMALE, 2020, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, EGO_ID, childId,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/generations")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("focusPersonId", EGO_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generations.length()").value(2))
                .andExpect(jsonPath("$.generations[0].index").value(0))
                .andExpect(jsonPath("$.generations[0].persons[0].id").value(EGO_ID.toString()))
                .andExpect(jsonPath("$.generations[1].index").value(1))
                .andExpect(jsonPath("$.generations[1].persons[0].id").value(childId.toString()));
    }

    @Test
    void getGenerations_halfSiblingViaSharedBioParent_atIndex0() throws Exception {
        UUID fatherId = UUID.randomUUID();
        UUID halfSiblingId = UUID.randomUUID();

        projectionStore.createPerson(new ProjectionPerson(
                fatherId, FAMILY_ID, "Father", Gender.MALE, 1960, null, false));
        projectionStore.createPerson(new ProjectionPerson(
                halfSiblingId, FAMILY_ID, "Half Sibling", Gender.FEMALE, 1992, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, fatherId, EGO_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, fatherId, halfSiblingId,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/generations")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("focusPersonId", EGO_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generations.length()").value(2))
                .andExpect(jsonPath("$.generations[0].index").value(-1))
                .andExpect(jsonPath("$.generations[0].persons[0].id").value(fatherId.toString()))
                .andExpect(jsonPath("$.generations[1].index").value(0))
                .andExpect(jsonPath("$.generations[1].persons.length()").value(2))
                .andExpect(jsonPath("$.generations[1].persons[*].id", hasItem(EGO_ID.toString())))
                .andExpect(jsonPath("$.generations[1].persons[*].id", hasItem(halfSiblingId.toString())));
    }

    @Test
    void getGenerations_adoptiveParentOnly_notIncluded() throws Exception {
        UUID adoptiveParentId = UUID.randomUUID();

        projectionStore.createPerson(new ProjectionPerson(
                adoptiveParentId, FAMILY_ID, "Adoptive Parent", Gender.MALE, 1955, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, adoptiveParentId, EGO_ID,
                ParentChildSubtype.ADOPTIVE, ParentRole.FATHER, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/generations")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("focusPersonId", EGO_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generations.length()").value(1))
                .andExpect(jsonPath("$.generations[0].index").value(0))
                .andExpect(jsonPath("$.generations[0].persons.length()").value(1))
                .andExpect(jsonPath("$.generations[0].persons[0].id").value(EGO_ID.toString()));
    }

    @Test
    void getGenerations_dissolvedBioEdge_ignored() throws Exception {
        UUID fatherId = UUID.randomUUID();

        projectionStore.createPerson(new ProjectionPerson(
                fatherId, FAMILY_ID, "Father", Gender.MALE, 1960, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, fatherId, EGO_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, true));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/generations")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("focusPersonId", EGO_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generations.length()").value(1))
                .andExpect(jsonPath("$.generations[0].index").value(0))
                .andExpect(jsonPath("$.generations[0].persons.length()").value(1))
                .andExpect(jsonPath("$.generations[0].persons[0].id").value(EGO_ID.toString()));
    }

    @Test
    void getGenerations_hiddenPerson_excluded() throws Exception {
        UUID hiddenParentId = UUID.randomUUID();

        projectionStore.createPerson(new ProjectionPerson(
                hiddenParentId, FAMILY_ID, "Hidden Parent", Gender.MALE, 1960, null, true));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, hiddenParentId, EGO_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/generations")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("focusPersonId", EGO_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generations.length()").value(1))
                .andExpect(jsonPath("$.generations[0].index").value(0))
                .andExpect(jsonPath("$.generations[0].persons.length()").value(1))
                .andExpect(jsonPath("$.generations[0].persons[0].id").value(EGO_ID.toString()));
    }

    @Test
    void getGenerations_hiddenFocus_returns404() throws Exception {
        UUID hiddenEgoId = UUID.randomUUID();

        projectionStore.createPerson(new ProjectionPerson(
                hiddenEgoId, FAMILY_ID, "Hidden Ego", Gender.MALE, 1990, null, true));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/generations")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("focusPersonId", hiddenEgoId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    @Test
    void getGenerations_diamondConflict_personAppearsOnceWithConflict() throws Exception {
        UUID grandparentId = UUID.randomUUID();
        UUID parent1Id = UUID.randomUUID();
        UUID parent2Id = UUID.randomUUID();

        projectionStore.createPerson(new ProjectionPerson(
                grandparentId, FAMILY_ID, "Grandparent", Gender.MALE, 1940, null, false));
        projectionStore.createPerson(new ProjectionPerson(
                parent1Id, FAMILY_ID, "Parent 1", Gender.MALE, 1960, null, false));
        projectionStore.createPerson(new ProjectionPerson(
                parent2Id, FAMILY_ID, "Parent 2", Gender.FEMALE, 1962, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, grandparentId, parent1Id,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, grandparentId, parent2Id,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, parent1Id, EGO_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, parent2Id, EGO_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/generations")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("focusPersonId", EGO_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generations.length()").value(3))
                .andExpect(jsonPath("$.generations[0].index").value(-2))
                .andExpect(jsonPath("$.generations[0].persons.length()").value(1))
                .andExpect(jsonPath("$.generations[0].persons[0].id").value(grandparentId.toString()))
                .andExpect(jsonPath("$.generations[0].persons[0].conflict").doesNotExist())
                .andExpect(jsonPath("$.generations[1].index").value(-1))
                .andExpect(jsonPath("$.generations[1].persons.length()").value(2))
                .andExpect(jsonPath("$.generations[2].index").value(0))
                .andExpect(jsonPath("$.generations[2].persons[0].id").value(EGO_ID.toString()));
    }

    @Test
    void getGenerations_actualConflict_differentPaths_markedConflict() throws Exception {
        UUID personA = UUID.randomUUID();
        UUID personB = UUID.randomUUID();

        projectionStore.createPerson(new ProjectionPerson(
                personA, FAMILY_ID, "Person A", Gender.MALE, 1960, null, false));
        projectionStore.createPerson(new ProjectionPerson(
                personB, FAMILY_ID, "Person B", Gender.MALE, 1990, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, personA, EGO_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, EGO_ID, personB,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, personA, personB,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/generations")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("focusPersonId", EGO_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generations[*].persons[?(@.id=='" + personB.toString() + "')].conflict").value(hasItem(true)));
    }

    @Test
    void getGenerations_multipleGenerations_sortedAscending() throws Exception {
        UUID grandparentId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        UUID grandchildId = UUID.randomUUID();

        projectionStore.createPerson(new ProjectionPerson(
                grandparentId, FAMILY_ID, "Grandparent", Gender.MALE, 1940, null, false));
        projectionStore.createPerson(new ProjectionPerson(
                parentId, FAMILY_ID, "Parent", Gender.MALE, 1960, null, false));
        projectionStore.createPerson(new ProjectionPerson(
                childId, FAMILY_ID, "Child", Gender.MALE, 2020, null, false));
        projectionStore.createPerson(new ProjectionPerson(
                grandchildId, FAMILY_ID, "Grandchild", Gender.MALE, 2040, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, grandparentId, parentId,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, parentId, EGO_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, EGO_ID, childId,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, childId, grandchildId,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/generations")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("focusPersonId", EGO_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generations.length()").value(5))
                .andExpect(jsonPath("$.generations[0].index").value(-2))
                .andExpect(jsonPath("$.generations[1].index").value(-1))
                .andExpect(jsonPath("$.generations[2].index").value(0))
                .andExpect(jsonPath("$.generations[3].index").value(1))
                .andExpect(jsonPath("$.generations[4].index").value(2));
    }

    @Test
    void getGenerations_personsWithinLayerSortedByDisplayNameThenId() throws Exception {
        UUID parentAId = UUID.randomUUID();
        UUID parentBId = UUID.randomUUID();

        projectionStore.createPerson(new ProjectionPerson(
                parentAId, FAMILY_ID, "Zoe", Gender.FEMALE, 1960, null, false));
        projectionStore.createPerson(new ProjectionPerson(
                parentBId, FAMILY_ID, "Adam", Gender.MALE, 1958, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, parentAId, EGO_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, parentBId, EGO_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/generations")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("focusPersonId", EGO_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generations[0].index").value(-1))
                .andExpect(jsonPath("$.generations[0].persons[0].displayName").value("Adam"))
                .andExpect(jsonPath("$.generations[0].persons[1].displayName").value("Zoe"));
    }

    @Test
    void getGenerations_spouseNotIncludedViaBioClimbOnly() throws Exception {
        UUID spouseId = UUID.randomUUID();

        projectionStore.createPerson(new ProjectionPerson(
                spouseId, FAMILY_ID, "Spouse", Gender.FEMALE, 1991, null, false));

        projectionStore.createMarriage(new ProjectionMarriage(
                UUID.randomUUID(), FAMILY_ID, EGO_ID, spouseId,
                MarriageStatus.ACTIVE, "2015-06-01", null, null));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/generations")
                        .header(AUTH_HEADER, bearerToken(MEMBER_USER_ID))
                        .param("focusPersonId", EGO_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generations.length()").value(1))
                .andExpect(jsonPath("$.generations[0].persons.length()").value(1))
                .andExpect(jsonPath("$.generations[0].persons[0].id").value(EGO_ID.toString()));
    }

    @Test
    void getGenerations_viewerRoleCanAccess() throws Exception {
        UUID viewerUserId = UUID.randomUUID();
        familyStore.addMemberWithRole(FAMILY_ID, viewerUserId, Role.VIEWER);

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/generations")
                        .header(AUTH_HEADER, bearerToken(viewerUserId))
                        .param("focusPersonId", EGO_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.familyId").value(FAMILY_ID.toString()));
    }
}
