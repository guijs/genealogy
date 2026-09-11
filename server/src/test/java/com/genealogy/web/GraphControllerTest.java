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

class GraphControllerTest extends BaseIntegrationTest {

    @Autowired
    private FamilyStore familyStore;

    @Autowired
    private ProjectionStore projectionStore;

    private static final UUID FAMILY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEMBER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID NON_MEMBER_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ROOT_PERSON_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID CHILD_PERSON_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID SPOUSE_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final String FAMILY_NAME = "Test Family";

    @BeforeEach
    void setUp() {
        cleanAllData();

        familyStore.createFamily(FAMILY_ID, FAMILY_NAME);
        familyStore.addMemberWithRole(FAMILY_ID, MEMBER_USER_ID, Role.ADMIN);

        projectionStore.createPerson(new ProjectionPerson(
                ROOT_PERSON_ID, FAMILY_ID, "Root Person", Gender.MALE, 1970, null, false));
    }

    @Test
    void getGraph_withoutUserIdHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .param("rootPersonId", ROOT_PERSON_ID.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("authentication required"));
    }

    @Test
    void getGraph_withNonMemberUser_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", NON_MEMBER_USER_ID.toString())
                        .param("rootPersonId", ROOT_PERSON_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    @Test
    void getGraph_withoutRootPersonId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", MEMBER_USER_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("rootPersonId query parameter required"));
    }

    @Test
    void getGraph_withInvalidRootPersonId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", MEMBER_USER_ID.toString())
                        .param("rootPersonId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid rootPersonId"));
    }

    @Test
    void getGraph_withDepth9_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", MEMBER_USER_ID.toString())
                        .param("rootPersonId", ROOT_PERSON_ID.toString())
                        .param("depth", "9"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("depth exceeds maximum allowed value of 8"));
    }

    @Test
    void getGraph_withDepth10_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", MEMBER_USER_ID.toString())
                        .param("rootPersonId", ROOT_PERSON_ID.toString())
                        .param("depth", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("depth exceeds maximum allowed value of 8"));
    }

    @Test
    void getGraph_withDefaultDepth_returns3() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", MEMBER_USER_ID.toString())
                        .param("rootPersonId", ROOT_PERSON_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depth").value(3));
    }

    @Test
    void getGraph_withInvalidDepth_usesDefault() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", MEMBER_USER_ID.toString())
                        .param("rootPersonId", ROOT_PERSON_ID.toString())
                        .param("depth", "invalid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depth").value(3));
    }

    @Test
    void getGraph_withZeroDepth_usesDefault() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", MEMBER_USER_ID.toString())
                        .param("rootPersonId", ROOT_PERSON_ID.toString())
                        .param("depth", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depth").value(3));
    }

    @Test
    void getGraph_withNegativeDepth_usesDefault() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", MEMBER_USER_ID.toString())
                        .param("rootPersonId", ROOT_PERSON_ID.toString())
                        .param("depth", "-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depth").value(3));
    }

    @Test
    void getGraph_happy200CamelCase() throws Exception {
        projectionStore.createPerson(new ProjectionPerson(
                CHILD_PERSON_ID, FAMILY_ID, "Child Person", Gender.FEMALE, 2000, null, false));

        UUID relId = UUID.randomUUID();
        projectionStore.createRelationship(new ProjectionRelationship(
                relId, FAMILY_ID, ROOT_PERSON_ID, CHILD_PERSON_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", MEMBER_USER_ID.toString())
                        .param("rootPersonId", ROOT_PERSON_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.familyId").value(FAMILY_ID.toString()))
                .andExpect(jsonPath("$.rootPersonId").value(ROOT_PERSON_ID.toString()))
                .andExpect(jsonPath("$.depth").value(3))
                .andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.persons").isArray())
                .andExpect(jsonPath("$.persons.length()").value(2))
                .andExpect(jsonPath("$.persons[*].id").exists())
                .andExpect(jsonPath("$.persons[*].displayName").exists())
                .andExpect(jsonPath("$.marriages").isArray())
                .andExpect(jsonPath("$.relationships").isArray())
                .andExpect(jsonPath("$.relationships.length()").value(1))
                .andExpect(jsonPath("$.relationships[0].type").value("PARENT_CHILD"))
                .andExpect(jsonPath("$.relationships[0].parentId").value(ROOT_PERSON_ID.toString()))
                .andExpect(jsonPath("$.relationships[0].childId").value(CHILD_PERSON_ID.toString()));
    }

    @Test
    void getGraph_divorcedMarriagePresent() throws Exception {
        projectionStore.createPerson(new ProjectionPerson(
                SPOUSE_ID, FAMILY_ID, "Ex-Spouse", Gender.FEMALE, 1975, null, false));

        UUID marriageId = UUID.randomUUID();
        projectionStore.createMarriage(new ProjectionMarriage(
                marriageId, FAMILY_ID, ROOT_PERSON_ID, SPOUSE_ID,
                MarriageStatus.DIVORCED, "1995-06-15", "2010-03-20", "irreconcilable differences"));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", MEMBER_USER_ID.toString())
                        .param("rootPersonId", ROOT_PERSON_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marriages").isArray())
                .andExpect(jsonPath("$.marriages.length()").value(1))
                .andExpect(jsonPath("$.marriages[0].status").value("divorced"))
                .andExpect(jsonPath("$.marriages[0].endedAt").value("2010-03-20"))
                .andExpect(jsonPath("$.marriages[0].endedReason").value("irreconcilable differences"))
                .andExpect(jsonPath("$.marriages[0].partnerIds", hasSize(2)));
    }

    @Test
    void getGraph_dissolvedRelationshipExcluded() throws Exception {
        projectionStore.createPerson(new ProjectionPerson(
                CHILD_PERSON_ID, FAMILY_ID, "Child Person", Gender.FEMALE, 2000, null, false));

        UUID activeRelId = UUID.randomUUID();
        projectionStore.createRelationship(new ProjectionRelationship(
                activeRelId, FAMILY_ID, ROOT_PERSON_ID, CHILD_PERSON_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));

        UUID dissolvedRelId = UUID.randomUUID();
        UUID anotherChildId = UUID.randomUUID();
        projectionStore.createPerson(new ProjectionPerson(
                anotherChildId, FAMILY_ID, "Another Child", Gender.MALE, 2005, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                dissolvedRelId, FAMILY_ID, ROOT_PERSON_ID, anotherChildId,
                ParentChildSubtype.ADOPTIVE, ParentRole.FATHER, null, true));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", MEMBER_USER_ID.toString())
                        .param("rootPersonId", ROOT_PERSON_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationships.length()").value(1))
                .andExpect(jsonPath("$.relationships[0].id").value(activeRelId.toString()));
    }

    @Test
    void getGraph_hiddenPersonExcluded() throws Exception {
        UUID hiddenPersonId = UUID.randomUUID();
        projectionStore.createPerson(new ProjectionPerson(
                hiddenPersonId, FAMILY_ID, "Hidden Person", Gender.MALE, 1990, null, true));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", MEMBER_USER_ID.toString())
                        .param("rootPersonId", ROOT_PERSON_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons.length()").value(1))
                .andExpect(jsonPath("$.persons[0].id").value(ROOT_PERSON_ID.toString()));
    }

    @Test
    void getGraph_hiddenPartnerMarriageExcluded() throws Exception {
        UUID hiddenSpouseId = UUID.randomUUID();
        projectionStore.createPerson(new ProjectionPerson(
                hiddenSpouseId, FAMILY_ID, "Hidden Spouse", Gender.FEMALE, 1975, null, true));

        UUID marriageId = UUID.randomUUID();
        projectionStore.createMarriage(new ProjectionMarriage(
                marriageId, FAMILY_ID, ROOT_PERSON_ID, hiddenSpouseId,
                MarriageStatus.ACTIVE, "2000-01-01", null, null));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", MEMBER_USER_ID.toString())
                        .param("rootPersonId", ROOT_PERSON_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marriages.length()").value(0));
    }

    @Test
    void getGraph_hiddenParentRelationshipExcluded() throws Exception {
        UUID hiddenParentId = UUID.randomUUID();
        projectionStore.createPerson(new ProjectionPerson(
                hiddenParentId, FAMILY_ID, "Hidden Parent", Gender.MALE, 1940, null, true));

        UUID relId = UUID.randomUUID();
        projectionStore.createRelationship(new ProjectionRelationship(
                relId, FAMILY_ID, hiddenParentId, ROOT_PERSON_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", MEMBER_USER_ID.toString())
                        .param("rootPersonId", ROOT_PERSON_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationships.length()").value(0));
    }

    @Test
    void getGraph_truncatedNoDangling() throws Exception {
        UUID gen1 = UUID.randomUUID();
        UUID gen2 = UUID.randomUUID();
        UUID gen3 = UUID.randomUUID();
        UUID gen4 = UUID.randomUUID();

        projectionStore.createPerson(new ProjectionPerson(gen1, FAMILY_ID, "Gen1", Gender.MALE, 2000, null, false));
        projectionStore.createPerson(new ProjectionPerson(gen2, FAMILY_ID, "Gen2", Gender.MALE, 2020, null, false));
        projectionStore.createPerson(new ProjectionPerson(gen3, FAMILY_ID, "Gen3", Gender.MALE, 2040, null, false));
        projectionStore.createPerson(new ProjectionPerson(gen4, FAMILY_ID, "Gen4", Gender.MALE, 2060, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, ROOT_PERSON_ID, gen1,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, gen1, gen2,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, gen2, gen3,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, gen3, gen4,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", MEMBER_USER_ID.toString())
                        .param("rootPersonId", ROOT_PERSON_ID.toString())
                        .param("depth", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.truncated").value(true))
                .andExpect(jsonPath("$.truncateReason").value("已达展开上限"))
                .andExpect(jsonPath("$.persons.length()").value(3))
                .andExpect(jsonPath("$.relationships.length()").value(2));
    }

    @Test
    void getGraph_truncatedMarriageNoDangling() throws Exception {
        UUID child = UUID.randomUUID();
        UUID grandchild = UUID.randomUUID();
        UUID grandchildSpouse = UUID.randomUUID();

        projectionStore.createPerson(new ProjectionPerson(SPOUSE_ID, FAMILY_ID, "Spouse", Gender.FEMALE, 1975, null, false));
        projectionStore.createPerson(new ProjectionPerson(child, FAMILY_ID, "Child", Gender.MALE, 2000, null, false));
        projectionStore.createPerson(new ProjectionPerson(grandchild, FAMILY_ID, "Grandchild", Gender.MALE, 2020, null, false));
        projectionStore.createPerson(new ProjectionPerson(grandchildSpouse, FAMILY_ID, "Grandchild Spouse", Gender.FEMALE, 2020, null, false));

        projectionStore.createMarriage(new ProjectionMarriage(
                UUID.randomUUID(), FAMILY_ID, ROOT_PERSON_ID, SPOUSE_ID,
                MarriageStatus.ACTIVE, null, null, null));

        projectionStore.createMarriage(new ProjectionMarriage(
                UUID.randomUUID(), FAMILY_ID, grandchild, grandchildSpouse,
                MarriageStatus.ACTIVE, null, null, null));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, ROOT_PERSON_ID, child,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, child, grandchild,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", MEMBER_USER_ID.toString())
                        .param("rootPersonId", ROOT_PERSON_ID.toString())
                        .param("depth", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.truncated").value(true))
                .andExpect(jsonPath("$.marriages.length()").value(1))
                .andExpect(jsonPath("$.marriages[0].partnerIds", hasItem(ROOT_PERSON_ID.toString())))
                .andExpect(jsonPath("$.marriages[0].partnerIds", hasItem(SPOUSE_ID.toString())));
    }

    @Test
    void getGraph_rootNotInFamily_returns404() throws Exception {
        UUID otherFamilyId = UUID.randomUUID();
        UUID otherPersonId = UUID.randomUUID();
        
        familyStore.createFamily(otherFamilyId, "Other Family");
        familyStore.addMemberWithRole(otherFamilyId, MEMBER_USER_ID, Role.ADMIN);
        
        projectionStore.createPerson(new ProjectionPerson(
                otherPersonId, otherFamilyId, "Other Person", Gender.MALE, 1980, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", MEMBER_USER_ID.toString())
                        .param("rootPersonId", otherPersonId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    @Test
    void getGraph_rootDoesNotExist_returns404() throws Exception {
        UUID nonExistentPersonId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", MEMBER_USER_ID.toString())
                        .param("rootPersonId", nonExistentPersonId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    @Test
    void getGraph_withDepth8_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", MEMBER_USER_ID.toString())
                        .param("rootPersonId", ROOT_PERSON_ID.toString())
                        .param("depth", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depth").value(8));
    }

    @Test
    void getGraph_personDeceased_flagSet() throws Exception {
        UUID deceasedId = UUID.randomUUID();
        projectionStore.createPerson(new ProjectionPerson(
                deceasedId, FAMILY_ID, "Deceased Person", Gender.MALE, 1900, 1980, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, deceasedId, ROOT_PERSON_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", MEMBER_USER_ID.toString())
                        .param("rootPersonId", ROOT_PERSON_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons[?(@.id=='" + deceasedId.toString() + "')].deceased").value(hasItem(true)))
                .andExpect(jsonPath("$.persons[?(@.id=='" + deceasedId.toString() + "')].deathYear").value(hasItem(1980)));
    }

    @Test
    void getGraph_relationshipWithMarriageId() throws Exception {
        projectionStore.createPerson(new ProjectionPerson(
                SPOUSE_ID, FAMILY_ID, "Spouse", Gender.FEMALE, 1975, null, false));
        projectionStore.createPerson(new ProjectionPerson(
                CHILD_PERSON_ID, FAMILY_ID, "Child", Gender.MALE, 2000, null, false));

        UUID marriageId = UUID.randomUUID();
        projectionStore.createMarriage(new ProjectionMarriage(
                marriageId, FAMILY_ID, ROOT_PERSON_ID, SPOUSE_ID,
                MarriageStatus.ACTIVE, null, null, null));

        UUID relId = UUID.randomUUID();
        projectionStore.createRelationship(new ProjectionRelationship(
                relId, FAMILY_ID, ROOT_PERSON_ID, CHILD_PERSON_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, marriageId, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", MEMBER_USER_ID.toString())
                        .param("rootPersonId", ROOT_PERSON_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationships[0].marriageId").value(marriageId.toString()));
    }

    @Test
    void getGraph_spouseIncludedViaMarriage() throws Exception {
        projectionStore.createPerson(new ProjectionPerson(
                SPOUSE_ID, FAMILY_ID, "Spouse", Gender.FEMALE, 1975, null, false));

        UUID marriageId = UUID.randomUUID();
        projectionStore.createMarriage(new ProjectionMarriage(
                marriageId, FAMILY_ID, ROOT_PERSON_ID, SPOUSE_ID,
                MarriageStatus.ACTIVE, null, null, null));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", MEMBER_USER_ID.toString())
                        .param("rootPersonId", ROOT_PERSON_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons.length()").value(2))
                .andExpect(jsonPath("$.persons[*].id", hasItem(ROOT_PERSON_ID.toString())))
                .andExpect(jsonPath("$.persons[*].id", hasItem(SPOUSE_ID.toString())))
                .andExpect(jsonPath("$.marriages.length()").value(1));
    }
}
