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

class GenerationNamesControllerTest extends BaseIntegrationTest {

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
    private static final UUID GRANDCHILD_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID GREAT_GRANDCHILD_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @BeforeEach
    void setUp() {
        cleanAllData();
        
        familyStore.createFamily(FAMILY_ID, "Test Family");
        familyStore.addMemberWithRole(FAMILY_ID, ADMIN_USER_ID, Role.ADMIN);
        familyStore.addMemberWithRole(FAMILY_ID, EDITOR_USER_ID, Role.EDITOR);
        familyStore.addMemberWithRole(FAMILY_ID, VIEWER_USER_ID, Role.VIEWER);

        personStore.addPerson(new Person(PROGENITOR_ID, FAMILY_ID, "Progenitor", "Ancestor"));
        personStore.addPerson(new Person(CHILD1_ID, FAMILY_ID, "Child", "One"));
        personStore.addPerson(new Person(GRANDCHILD_ID, FAMILY_ID, "Grandchild", "One"));
        personStore.addPerson(new Person(GREAT_GRANDCHILD_ID, FAMILY_ID, "GreatGrandchild", "One"));

        projectionStore.createPerson(new ProjectionPerson(PROGENITOR_ID, FAMILY_ID, "Progenitor Ancestor", Gender.MALE, 1900, null, false));
        projectionStore.createPerson(new ProjectionPerson(CHILD1_ID, FAMILY_ID, "Child One", Gender.MALE, 1930, null, false));
        projectionStore.createPerson(new ProjectionPerson(GRANDCHILD_ID, FAMILY_ID, "Grandchild One", Gender.MALE, 1960, null, false));
        projectionStore.createPerson(new ProjectionPerson(GREAT_GRANDCHILD_ID, FAMILY_ID, "GreatGrandchild One", Gender.MALE, 1990, null, false));

        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, PROGENITOR_ID, CHILD1_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, CHILD1_ID, GRANDCHILD_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));
        projectionStore.createRelationship(new ProjectionRelationship(
                UUID.randomUUID(), FAMILY_ID, GRANDCHILD_ID, GREAT_GRANDCHILD_ID,
                ParentChildSubtype.BIOLOGICAL, ParentRole.FATHER, null, false));

        familyStore.updateProgenitor(FAMILY_ID, PROGENITOR_ID);
    }

    @Test
    void getGenerationNames_whenEmpty_returnsNullAndDefaultA() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/generation-names")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generation_names").isEmpty())
                .andExpect(jsonPath("$.generation_name_align").value("A"));
    }

    @Test
    void setGenerationNames_asAdmin_succeeds() throws Exception {
        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/generation-names")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"generation_names\":[\"甲\",\"乙\"],\"generation_name_align\":\"A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generation_names[0]").value("甲"))
                .andExpect(jsonPath("$.generation_names[1]").value("乙"))
                .andExpect(jsonPath("$.generation_name_align").value("A"));
    }

    @Test
    void setGenerationNames_asEditor_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/generation-names")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"generation_names\":[\"甲\",\"乙\"],\"generation_name_align\":\"A\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("admin access required"));
    }

    @Test
    void setGenerationNames_asViewer_returns403WriteAccessRequired() throws Exception {
        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/generation-names")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"generation_names\":[\"甲\",\"乙\"],\"generation_name_align\":\"A\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("write access required"));
    }

    @Test
    void setGenerationNames_asNonMember_returns404() throws Exception {
        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/generation-names")
                        .header(AUTH_HEADER, bearerToken(NON_MEMBER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"generation_names\":[\"甲\",\"乙\"],\"generation_name_align\":\"A\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void setGenerationNames_invalidAlign_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/generation-names")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"generation_names\":[\"甲\",\"乙\"],\"generation_name_align\":\"C\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid generation_name_align, must be 'A' or 'B'"));
    }

    @Test
    void lineage_withAlignA_gen1NullGen2FirstName() throws Exception {
        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/generation-names")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"generation_names\":[\"甲\",\"乙\"],\"generation_name_align\":\"A\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/lineage")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generation_name_align").value("A"))
                .andExpect(jsonPath("$.generations[0].index").value(1))
                .andExpect(jsonPath("$.generations[0].generation_name").isEmpty())
                .andExpect(jsonPath("$.generations[1].index").value(2))
                .andExpect(jsonPath("$.generations[1].generation_name").value("甲"))
                .andExpect(jsonPath("$.generations[2].index").value(3))
                .andExpect(jsonPath("$.generations[2].generation_name").value("乙"))
                .andExpect(jsonPath("$.generations[3].index").value(4))
                .andExpect(jsonPath("$.generations[3].generation_name").isEmpty());
    }

    @Test
    void lineage_withAlignB_gen1FirstName() throws Exception {
        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/generation-names")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"generation_names\":[\"甲\",\"乙\"],\"generation_name_align\":\"B\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/lineage")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generation_name_align").value("B"))
                .andExpect(jsonPath("$.generations[0].index").value(1))
                .andExpect(jsonPath("$.generations[0].generation_name").value("甲"))
                .andExpect(jsonPath("$.generations[1].index").value(2))
                .andExpect(jsonPath("$.generations[1].generation_name").value("乙"))
                .andExpect(jsonPath("$.generations[2].index").value(3))
                .andExpect(jsonPath("$.generations[2].generation_name").isEmpty())
                .andExpect(jsonPath("$.generations[3].index").value(4))
                .andExpect(jsonPath("$.generations[3].generation_name").isEmpty());
    }

    @Test
    void lineage_withExplicitEmptySlot_returnsEmptyString() throws Exception {
        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/generation-names")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"generation_names\":[\"甲\",\"\",\"丙\"],\"generation_name_align\":\"A\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/lineage")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generations[0].index").value(1))
                .andExpect(jsonPath("$.generations[0].generation_name").isEmpty())
                .andExpect(jsonPath("$.generations[1].index").value(2))
                .andExpect(jsonPath("$.generations[1].generation_name").value("甲"))
                .andExpect(jsonPath("$.generations[2].index").value(3))
                .andExpect(jsonPath("$.generations[2].generation_name").value(""))
                .andExpect(jsonPath("$.generations[3].index").value(4))
                .andExpect(jsonPath("$.generations[3].generation_name").value("丙"));
    }

    @Test
    void getFamily_includesGenerationNamesAndAlign() throws Exception {
        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/generation-names")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"generation_names\":[\"德\",\"仁\"],\"generation_name_align\":\"B\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID)
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generation_names[0]").value("德"))
                .andExpect(jsonPath("$.generation_names[1]").value("仁"))
                .andExpect(jsonPath("$.generation_name_align").value("B"));
    }

    @Test
    void setGenerationNames_withoutAlign_keepsExistingAlign() throws Exception {
        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/generation-names")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"generation_names\":[\"甲\"],\"generation_name_align\":\"B\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/generation-names")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"generation_names\":[\"甲\",\"乙\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generation_names.length()").value(2))
                .andExpect(jsonPath("$.generation_name_align").value("B"));
    }

    @Test
    void setGenerationNames_tooManyEntries_returns400() throws Exception {
        StringBuilder json = new StringBuilder("{\"generation_names\":[");
        for (int i = 0; i < 201; i++) {
            if (i > 0) json.append(",");
            json.append("\"x\"");
        }
        json.append("]}");

        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/generation-names")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("generation_names exceeds maximum of 200 entries"));
    }

    @Test
    void setGenerationNames_nameTooLong_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/generation-names")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"generation_names\":[\"12345678901234567\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("each generation name must be at most 16 characters"));
    }

    @Test
    void lineage_bioDownwardOnly_noNameMutation() throws Exception {
        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/generation-names")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"generation_names\":[\"甲\",\"乙\",\"丙\"],\"generation_name_align\":\"A\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/lineage")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generations[0].persons[0].display_name").value("Progenitor Ancestor"))
                .andExpect(jsonPath("$.generations[1].persons[0].display_name").value("Child One"))
                .andExpect(jsonPath("$.generations[2].persons[0].display_name").value("Grandchild One"))
                .andExpect(jsonPath("$.generations[3].persons[0].display_name").value("GreatGrandchild One"));
    }

    @Test
    void getGenerationNames_noAuthHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/generation-names"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void setGenerationNames_noAuthHeader_returns401() throws Exception {
        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/generation-names")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"generation_names\":[\"甲\"]}"))
                .andExpect(status().isUnauthorized());
    }
}
