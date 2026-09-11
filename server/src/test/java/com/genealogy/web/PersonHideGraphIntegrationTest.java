package com.genealogy.web;

import com.genealogy.domain.family.Role;
import com.genealogy.domain.person.Person;
import com.genealogy.domain.projection.*;
import com.genealogy.domain.union.Union;
import com.genealogy.store.*;
import com.genealogy.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PersonHideGraphIntegrationTest extends BaseIntegrationTest {

    private static final String AUTH_HEADER = "Authorization";

    @Autowired
    private FamilyStore familyStore;

    @Autowired
    private PersonStore personStore;

    @Autowired
    private ProjectionStore projectionStore;

    @Autowired
    private UnionStore unionStore;

    private static final UUID FAMILY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PARENT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID PARTNER_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID CHILD_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final String FAMILY_NAME = "Test Family";

    @BeforeEach
    void setUp() {
        cleanAllData();

        familyStore.createFamily(FAMILY_ID, FAMILY_NAME);
        familyStore.addMemberWithRole(FAMILY_ID, ADMIN_USER_ID, Role.ADMIN);

        personStore.addPerson(new Person(PARENT_ID, FAMILY_ID, "Parent", "Person"));
        personStore.addPerson(new Person(PARTNER_ID, FAMILY_ID, "Partner", "Person"));
        personStore.addPerson(new Person(CHILD_ID, FAMILY_ID, "Child", "Person"));
    }

    @Test
    void hideParent_relationshipWithHiddenParentExcludedFromGraph() throws Exception {
        UUID unionId = UUID.randomUUID();
        Union union = new Union(unionId, FAMILY_ID, PARENT_ID, PARTNER_ID,
                MarriageStatus.ACTIVE, "1975-01-01", null, null);
        unionStore.addUnion(union);
        projectionStore.createMarriage(new ProjectionMarriage(
                unionId, FAMILY_ID, PARENT_ID, PARTNER_ID,
                MarriageStatus.ACTIVE, "1975-01-01", null, null));

        UUID relationshipId = UUID.randomUUID();
        projectionStore.createRelationship(new ProjectionRelationship(
                relationshipId, FAMILY_ID, PARENT_ID, CHILD_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, unionId, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .param("rootPersonId", CHILD_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons", hasSize(3)))
                .andExpect(jsonPath("$.marriages", hasSize(1)))
                .andExpect(jsonPath("$.relationships", hasSize(1)));

        String hideBody = """
            {"confirm_hide_with_active_union": true}
            """;
        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons/" + PARENT_ID + "/hide")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hideBody))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .param("rootPersonId", CHILD_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons", hasSize(2)))
                .andExpect(jsonPath("$.marriages", hasSize(0)))
                .andExpect(jsonPath("$.relationships", hasSize(0)));
    }

    @Test
    void hideChild_relationshipWithHiddenChildExcludedFromGraph() throws Exception {
        UUID relationshipId = UUID.randomUUID();
        projectionStore.createRelationship(new ProjectionRelationship(
                relationshipId, FAMILY_ID, PARENT_ID, CHILD_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .param("rootPersonId", PARENT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons", hasSize(2)))
                .andExpect(jsonPath("$.relationships", hasSize(1)));

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons/" + CHILD_ID + "/hide")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .param("rootPersonId", PARENT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons", hasSize(1)))
                .andExpect(jsonPath("$.persons[0].id").value(PARENT_ID.toString()))
                .andExpect(jsonPath("$.relationships", hasSize(0)));
    }

    @Test
    void fullScenario_createUnionAndRelationship_hideOnePartner_graphExcludesAppropriately() throws Exception {
        UUID unionId = UUID.randomUUID();
        Union union = new Union(unionId, FAMILY_ID, PARENT_ID, PARTNER_ID,
                MarriageStatus.ACTIVE, "1975-01-01", null, null);
        unionStore.addUnion(union);
        projectionStore.createMarriage(new ProjectionMarriage(
                unionId, FAMILY_ID, PARENT_ID, PARTNER_ID,
                MarriageStatus.ACTIVE, "1975-01-01", null, null));

        UUID rel1Id = UUID.randomUUID();
        projectionStore.createRelationship(new ProjectionRelationship(
                rel1Id, FAMILY_ID, PARENT_ID, CHILD_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, unionId, false));

        UUID rel2Id = UUID.randomUUID();
        projectionStore.createRelationship(new ProjectionRelationship(
                rel2Id, FAMILY_ID, PARTNER_ID, CHILD_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.MOTHER, unionId, false));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .param("rootPersonId", CHILD_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons", hasSize(3)))
                .andExpect(jsonPath("$.marriages", hasSize(1)))
                .andExpect(jsonPath("$.relationships", hasSize(2)));

        String hideBody = """
            {"confirm_hide_with_active_union": true}
            """;
        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons/" + PARENT_ID + "/hide")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hideBody))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .param("rootPersonId", CHILD_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons", hasSize(2)))
                .andExpect(jsonPath("$.marriages", hasSize(0)))
                .andExpect(jsonPath("$.relationships", hasSize(1)))
                .andExpect(jsonPath("$.relationships[0].parentId").value(PARTNER_ID.toString()));

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons/" + PARENT_ID + "/restore")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .param("rootPersonId", CHILD_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons", hasSize(3)))
                .andExpect(jsonPath("$.marriages", hasSize(1)))
                .andExpect(jsonPath("$.relationships", hasSize(2)));
    }
}
