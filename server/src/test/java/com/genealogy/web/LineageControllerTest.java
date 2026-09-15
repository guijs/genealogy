package com.genealogy.web;

import com.genealogy.domain.family.Role;
import com.genealogy.domain.person.Person;
import com.genealogy.domain.projection.*;
import com.genealogy.store.FamilyStore;
import com.genealogy.store.PersonStore;
import com.genealogy.store.ProjectionStore;
import com.genealogy.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LineageControllerTest extends BaseIntegrationTest {

    private static final String AUTH_HEADER = "Authorization";

    @Autowired
    private FamilyStore familyStore;

    @Autowired
    private PersonStore personStore;

    @Autowired
    private ProjectionStore projectionStore;

    private static final UUID FAMILY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID EDITOR_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID VIEWER_USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID NON_MEMBER_USER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    
    private static final UUID PROGENITOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CHILD1_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID CHILD2_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID GRANDCHILD_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID ADOPTIVE_CHILD_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final UUID HIDDEN_PERSON_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    private static final UUID UNCONNECTED_PERSON_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OTHER_FAMILY_PERSON_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID OTHER_FAMILY_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");

    @BeforeEach
    void setUp() {
        cleanAllData();
        
        familyStore.createFamily(FAMILY_ID, "Test Family");
        familyStore.addMemberWithRole(FAMILY_ID, ADMIN_USER_ID, Role.ADMIN);
        familyStore.addMemberWithRole(FAMILY_ID, EDITOR_USER_ID, Role.EDITOR);
        familyStore.addMemberWithRole(FAMILY_ID, VIEWER_USER_ID, Role.VIEWER);

        personStore.addPerson(new Person(PROGENITOR_ID, FAMILY_ID, "Progenitor", "Ancestor"));
        personStore.addPerson(new Person(CHILD1_ID, FAMILY_ID, "Child", "One"));
        personStore.addPerson(new Person(CHILD2_ID, FAMILY_ID, "Child", "Two"));
        personStore.addPerson(new Person(GRANDCHILD_ID, FAMILY_ID, "Grandchild", "One"));
        personStore.addPerson(new Person(ADOPTIVE_CHILD_ID, FAMILY_ID, "Adoptive", "Child"));
        personStore.addPerson(new Person(HIDDEN_PERSON_ID, FAMILY_ID, "Hidden", "Person"));
        personStore.addPerson(new Person(UNCONNECTED_PERSON_ID, FAMILY_ID, "Unconnected", "Person"));

        familyStore.createFamily(OTHER_FAMILY_ID, "Other Family");
        personStore.addPerson(new Person(OTHER_FAMILY_PERSON_ID, OTHER_FAMILY_ID, "Other", "Person"));

        projectionStore.createPerson(new ProjectionPerson(PROGENITOR_ID, FAMILY_ID, "Progenitor Ancestor", Gender.MALE, 1900, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD1_ID, FAMILY_ID, "Child One", Gender.MALE, 1930, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD2_ID, FAMILY_ID, "Child Two", Gender.FEMALE, 1935, null, false));
        projectionStore.createPerson(new ProjectionPerson(GRANDCHILD_ID, FAMILY_ID, "Grandchild One", Gender.MALE, 1960, null, false));
        projectionStore.createPerson(new ProjectionPerson(ADOPTIVE_CHILD_ID, FAMILY_ID, "Adoptive Child", Gender.MALE, 1940, null, false));
        projectionStore.createPerson(new ProjectionPerson(HIDDEN_PERSON_ID, FAMILY_ID, "Hidden Person", Gender.MALE, 1920, null, true));
        projectionStore.createPerson(new ProjectionPerson(UNCONNECTED_PERSON_ID, FAMILY_ID, "Unconnected Person", Gender.MALE, 1950, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, PROGENITOR_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, PROGENITOR_ID, CHILD2_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, CHILD1_ID, GRANDCHILD_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, PROGENITOR_ID, ADOPTIVE_CHILD_ID,
                ParentChildSubtype.ADOPTIVE, ParentRole.FATHER, null, false));
    }

    @Test
    void getLineage_whenProgenitorNull_returns200WithEmptyGenerations() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/lineage")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.family_id").value(FAMILY_ID.toString()))
                .andExpect(jsonPath("$.progenitor_person_id").isEmpty())
                .andExpect(jsonPath("$.generations").isArray())
                .andExpect(jsonPath("$.generations.length()").value(0));
    }

    @Test
    void setProgenitor_asAdmin_succeeds() throws Exception {
        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/progenitor")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"person_id\":\"" + PROGENITOR_ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progenitor_person_id").value(PROGENITOR_ID.toString()));
    }

    @Test
    void getLineage_afterSetProgenitor_showsCorrectGenerations() throws Exception {
        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/progenitor")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"person_id\":\"" + PROGENITOR_ID + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/lineage")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.family_id").value(FAMILY_ID.toString()))
                .andExpect(jsonPath("$.progenitor_person_id").value(PROGENITOR_ID.toString()))
                .andExpect(jsonPath("$.generations.length()").value(3))
                .andExpect(jsonPath("$.generations[0].index").value(1))
                .andExpect(jsonPath("$.generations[0].persons.length()").value(1))
                .andExpect(jsonPath("$.generations[0].persons[0].id").value(PROGENITOR_ID.toString()))
                .andExpect(jsonPath("$.generations[1].index").value(2))
                .andExpect(jsonPath("$.generations[1].persons.length()").value(2))
                .andExpect(jsonPath("$.generations[2].index").value(3))
                .andExpect(jsonPath("$.generations[2].persons.length()").value(1))
                .andExpect(jsonPath("$.generations[2].persons[0].id").value(GRANDCHILD_ID.toString()));
    }

    @Test
    void setProgenitor_asEditor_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/progenitor")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"person_id\":\"" + PROGENITOR_ID + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("admin access required"));
    }

    @Test
    void setProgenitor_asViewer_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/progenitor")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"person_id\":\"" + PROGENITOR_ID + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("admin access required"));
    }

    @Test
    void setProgenitor_nonMember_returns404() throws Exception {
        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/progenitor")
                        .header(AUTH_HEADER, bearerToken(NON_MEMBER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"person_id\":\"" + PROGENITOR_ID + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void setProgenitor_withHiddenPerson_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/progenitor")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"person_id\":\"" + HIDDEN_PERSON_ID + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("cannot set hidden person as progenitor"));
    }

    @Test
    void setProgenitor_withOtherFamilyPerson_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/progenitor")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"person_id\":\"" + OTHER_FAMILY_PERSON_ID + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("person not in family"));
    }

    @Test
    void setProgenitor_withNonExistentPerson_returns404() throws Exception {
        UUID nonExistentId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/progenitor")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"person_id\":\"" + nonExistentId + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("person not found"));
    }

    @Test
    void getLineage_adoptiveChildNotInGenerations() throws Exception {
        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/progenitor")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"person_id\":\"" + PROGENITOR_ID + "\"}"))
                .andExpect(status().isOk());

        String response = mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/lineage")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(response)
                .doesNotContain(ADOPTIVE_CHILD_ID.toString());
    }

    @Test
    void getLineage_dissolvedBioEdgeIgnored() throws Exception {
        UUID dissolvedChildId = UUID.fromString("88888888-8888-8888-8888-888888888888");
        personStore.addPerson(new Person(dissolvedChildId, FAMILY_ID, "Dissolved", "Child"));
        projectionStore.createPerson(new ProjectionPerson(dissolvedChildId, FAMILY_ID, "Dissolved Child", Gender.MALE, 1932, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, PROGENITOR_ID, dissolvedChildId,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, true));

        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/progenitor")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"person_id\":\"" + PROGENITOR_ID + "\"}"))
                .andExpect(status().isOk());

        String response = mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/lineage")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(response)
                .doesNotContain(dissolvedChildId.toString());
    }

    @Test
    void getLineage_unconnectedPersonNotInGenerations() throws Exception {
        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/progenitor")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"person_id\":\"" + PROGENITOR_ID + "\"}"))
                .andExpect(status().isOk());

        String response = mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/lineage")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(response)
                .doesNotContain(UNCONNECTED_PERSON_ID.toString());
    }

    @Test
    void getLineage_conflictFlagWhenMultiPath() throws Exception {
        UUID conflictPersonId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        personStore.addPerson(new Person(conflictPersonId, FAMILY_ID, "Conflict", "Person"));
        projectionStore.createPerson(new ProjectionPerson(conflictPersonId, FAMILY_ID, "Conflict Person", Gender.MALE, 1950, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, PROGENITOR_ID, conflictPersonId,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, CHILD1_ID, conflictPersonId,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));

        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/progenitor")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"person_id\":\"" + PROGENITOR_ID + "\"}"))
                .andExpect(status().isOk());

        String response = mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/lineage")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(response)
                .contains("\"id\":\"" + conflictPersonId + "\"")
                .contains("\"conflict\":true");
    }

    @Test
    void clearProgenitor_returnsEmptyGenerations() throws Exception {
        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/progenitor")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"person_id\":\"" + PROGENITOR_ID + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/lineage")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generations.length()").value(3));

        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/progenitor")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"person_id\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progenitor_person_id").isEmpty());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/lineage")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progenitor_person_id").isEmpty())
                .andExpect(jsonPath("$.generations.length()").value(0));
    }

    @Test
    void getLineage_noAuthHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/lineage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void setProgenitor_noAuthHeader_returns401() throws Exception {
        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/progenitor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"person_id\":\"" + PROGENITOR_ID + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getFamily_includesProgenitorPersonId() throws Exception {
        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/progenitor")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"person_id\":\"" + PROGENITOR_ID + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID)
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progenitor_person_id").value(PROGENITOR_ID.toString()));
    }

    @Test
    void getFamily_whenProgenitorNull_returnsNullProgenitorPersonId() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID)
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progenitor_person_id").isEmpty());
    }

    @Test
    void getLineage_hiddenPersonNotInGenerations() throws Exception {
        UUID parentOfHiddenId = UUID.fromString("11111111-1111-1111-1111-222222222222");
        personStore.addPerson(new Person(parentOfHiddenId, FAMILY_ID, "Parent", "OfHidden"));
        projectionStore.createPerson(new ProjectionPerson(parentOfHiddenId, FAMILY_ID, "Parent OfHidden", Gender.MALE, 1890, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, parentOfHiddenId, HIDDEN_PERSON_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));

        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/progenitor")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"person_id\":\"" + parentOfHiddenId + "\"}"))
                .andExpect(status().isOk());

        String response = mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/lineage")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(response)
                .doesNotContain(HIDDEN_PERSON_ID.toString());
    }
}
