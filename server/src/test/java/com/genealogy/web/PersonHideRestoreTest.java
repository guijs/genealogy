package com.genealogy.web;

import com.genealogy.domain.family.Role;
import com.genealogy.domain.person.Person;
import com.genealogy.domain.projection.Gender;
import com.genealogy.domain.projection.MarriageStatus;
import com.genealogy.domain.projection.ProjectionMarriage;
import com.genealogy.domain.projection.ProjectionPerson;
import com.genealogy.domain.union.Union;
import com.genealogy.store.FamilyStore;
import com.genealogy.store.PersonStore;
import com.genealogy.store.ProjectionStore;
import com.genealogy.store.UnionStore;
import com.genealogy.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PersonHideRestoreTest extends BaseIntegrationTest {

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
    private static final UUID VIEWER_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID NON_MEMBER_USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID PERSON_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID PARTNER_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final String FAMILY_NAME = "Test Family";

    @BeforeEach
    void setUp() {
        cleanAllData();

        familyStore.createFamily(FAMILY_ID, FAMILY_NAME);
        familyStore.addMemberWithRole(FAMILY_ID, ADMIN_USER_ID, Role.ADMIN);
        familyStore.addMemberWithRole(FAMILY_ID, VIEWER_USER_ID, Role.VIEWER);

        personStore.addPerson(new Person(PERSON_ID, FAMILY_ID, "Test", "Person"));

        personStore.addPerson(new Person(PARTNER_ID, FAMILY_ID, "Partner", "Person"));
        projectionStore.createPerson(new ProjectionPerson(
                PARTNER_ID, FAMILY_ID, "Partner Person", Gender.FEMALE, 1982, null, false));
    }

    @Test
    void hidePerson_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons/" + PERSON_ID + "/hide")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("authentication required"));
    }

    @Test
    void hidePerson_nonMember_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons/" + PERSON_ID + "/hide")
                        .header("X-User-Id", NON_MEMBER_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    @Test
    void hidePerson_viewer_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons/" + PERSON_ID + "/hide")
                        .header("X-User-Id", VIEWER_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("write access required"));
    }

    @Test
    void hidePerson_unknownPerson_returns404() throws Exception {
        UUID unknownPersonId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons/" + unknownPersonId + "/hide")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    @Test
    void hidePerson_wrongFamily_returns404() throws Exception {
        UUID otherFamilyId = UUID.randomUUID();
        familyStore.createFamily(otherFamilyId, "Other Family");
        familyStore.addMemberWithRole(otherFamilyId, ADMIN_USER_ID, Role.ADMIN);

        mockMvc.perform(post("/api/v1/families/" + otherFamilyId + "/persons/" + PERSON_ID + "/hide")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    @Test
    void hidePerson_noActiveUnion_returns200_andExcludedFromGraph() throws Exception {
        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons/" + PERSON_ID + "/hide")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        assertThat(projectionStore.getPerson(PERSON_ID).get().isHidden()).isTrue();

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .param("rootPersonId", PARTNER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons", hasSize(1)))
                .andExpect(jsonPath("$.persons[0].id").value(PARTNER_ID.toString()));
    }

    @Test
    void hidePerson_withActiveUnion_noConfirm_returns409() throws Exception {
        UUID unionId = UUID.randomUUID();
        Union union = new Union(unionId, FAMILY_ID, PERSON_ID, PARTNER_ID,
                MarriageStatus.ACTIVE, "2020-01-01", null, null);
        unionStore.addUnion(union);
        projectionStore.createMarriage(new ProjectionMarriage(
                unionId, FAMILY_ID, PERSON_ID, PARTNER_ID,
                MarriageStatus.ACTIVE, "2020-01-01", null, null));

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons/" + PERSON_ID + "/hide")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("person has an active union; set confirm_hide_with_active_union to true or end the union first"));
    }

    @Test
    void hidePerson_withActiveUnion_withConfirm_returns200() throws Exception {
        UUID unionId = UUID.randomUUID();
        Union union = new Union(unionId, FAMILY_ID, PERSON_ID, PARTNER_ID,
                MarriageStatus.ACTIVE, "2020-01-01", null, null);
        unionStore.addUnion(union);
        projectionStore.createMarriage(new ProjectionMarriage(
                unionId, FAMILY_ID, PERSON_ID, PARTNER_ID,
                MarriageStatus.ACTIVE, "2020-01-01", null, null));

        String body = """
            {"confirm_hide_with_active_union": true}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons/" + PERSON_ID + "/hide")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        assertThat(projectionStore.getPerson(PERSON_ID).get().isHidden()).isTrue();
    }

    @Test
    void hidePerson_withEndedUnion_noConfirmNeeded_returns200() throws Exception {
        UUID unionId = UUID.randomUUID();
        Union union = new Union(unionId, FAMILY_ID, PERSON_ID, PARTNER_ID,
                MarriageStatus.DIVORCED, "2020-01-01", "2022-01-01", "divorced");
        unionStore.addUnion(union);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons/" + PERSON_ID + "/hide")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        assertThat(projectionStore.getPerson(PERSON_ID).get().isHidden()).isTrue();
    }

    @Test
    void restorePerson_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons/" + PERSON_ID + "/restore")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("authentication required"));
    }

    @Test
    void restorePerson_viewer_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons/" + PERSON_ID + "/restore")
                        .header("X-User-Id", VIEWER_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("write access required"));
    }

    @Test
    void restorePerson_afterHide_returns200_andAppearsInGraph() throws Exception {
        projectionStore.upsertPerson(new ProjectionPerson(
                PERSON_ID, FAMILY_ID, "Test Person", Gender.MALE, 1980, null, true));

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons/" + PERSON_ID + "/restore")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        assertThat(projectionStore.getPerson(PERSON_ID).get().isHidden()).isFalse();

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .param("rootPersonId", PERSON_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons", hasSize(1)))
                .andExpect(jsonPath("$.persons[0].id").value(PERSON_ID.toString()));
    }

    @Test
    void restorePerson_unknownPerson_returns404() throws Exception {
        UUID unknownPersonId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons/" + unknownPersonId + "/restore")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    @Test
    void patchAfterHide_preservesHiddenFlag() throws Exception {
        projectionStore.upsertPerson(new ProjectionPerson(
                PERSON_ID, FAMILY_ID, "Test Person", Gender.MALE, 1980, null, true));

        String body = """
            {"first_name": "Updated"}
            """;

        mockMvc.perform(patch("/api/v1/families/" + FAMILY_ID + "/persons/" + PERSON_ID)
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        assertThat(projectionStore.getPerson(PERSON_ID).get().isHidden()).isTrue();
        assertThat(projectionStore.getPerson(PERSON_ID).get().getDisplayName()).isEqualTo("Updated Person");

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .param("rootPersonId", PARTNER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons", hasSize(1)))
                .andExpect(jsonPath("$.persons[0].id").value(PARTNER_ID.toString()));
    }

    @Test
    void hidePerson_marriageWithHiddenPartnerExcludedFromGraph() throws Exception {
        UUID unionId = UUID.randomUUID();
        Union union = new Union(unionId, FAMILY_ID, PERSON_ID, PARTNER_ID,
                MarriageStatus.ACTIVE, "2020-01-01", null, null);
        unionStore.addUnion(union);
        projectionStore.createMarriage(new ProjectionMarriage(
                unionId, FAMILY_ID, PERSON_ID, PARTNER_ID,
                MarriageStatus.ACTIVE, "2020-01-01", null, null));

        String body = """
            {"confirm_hide_with_active_union": true}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons/" + PERSON_ID + "/hide")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .param("rootPersonId", PARTNER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons", hasSize(1)))
                .andExpect(jsonPath("$.persons[0].id").value(PARTNER_ID.toString()))
                .andExpect(jsonPath("$.marriages", hasSize(0)));
    }

    @Test
    void listPersons_stillReturnsHiddenPersons() throws Exception {
        projectionStore.upsertPerson(new ProjectionPerson(
                PERSON_ID, FAMILY_ID, "Test Person", Gender.MALE, 1980, null, true));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/persons")
                        .header("X-User-Id", ADMIN_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons", hasSize(2)));
    }

    @Test
    void hidePerson_editor_returns200() throws Exception {
        UUID editorUserId = UUID.randomUUID();
        familyStore.addMemberWithRole(FAMILY_ID, editorUserId, Role.EDITOR);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons/" + PERSON_ID + "/hide")
                        .header("X-User-Id", editorUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        assertThat(projectionStore.getPerson(PERSON_ID).get().isHidden()).isTrue();
    }

    @Test
    void restorePerson_editor_returns200() throws Exception {
        UUID editorUserId = UUID.randomUUID();
        familyStore.addMemberWithRole(FAMILY_ID, editorUserId, Role.EDITOR);

        projectionStore.upsertPerson(new ProjectionPerson(
                PERSON_ID, FAMILY_ID, "Test Person", Gender.MALE, 1980, null, true));

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons/" + PERSON_ID + "/restore")
                        .header("X-User-Id", editorUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        assertThat(projectionStore.getPerson(PERSON_ID).get().isHidden()).isFalse();
    }

    @Test
    void hidePerson_emptyBody_noActiveUnion_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons/" + PERSON_ID + "/hide")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        assertThat(projectionStore.getPerson(PERSON_ID).get().isHidden()).isTrue();
    }

    @Test
    void hidePerson_invalidPersonIdFormat_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons/not-a-uuid/hide")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    @Test
    void restorePerson_invalidPersonIdFormat_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/persons/not-a-uuid/restore")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }
}
