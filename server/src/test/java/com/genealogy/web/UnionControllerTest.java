package com.genealogy.web;

import com.genealogy.domain.family.Role;
import com.genealogy.domain.person.Person;
import com.genealogy.domain.projection.MarriageStatus;
import com.genealogy.domain.union.Union;
import com.genealogy.store.*;
import com.genealogy.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UnionControllerTest extends BaseIntegrationTest {

    private static final String AUTH_HEADER = "Authorization";

    @Autowired
    private FamilyStore familyStore;

    @Autowired
    private PersonStore personStore;

    @Autowired
    private UnionStore unionStore;

    @Autowired
    private ProjectionStore projectionStore;

    private static final UUID FAMILY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID VIEWER_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID NON_MEMBER_USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID PERSON1_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID PERSON2_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID PERSON3_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID PERSON_NOT_IN_FAMILY_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final String FAMILY_NAME = "Test Family";

    @BeforeEach
    void setUp() {
        cleanAllData();

        familyStore.createFamily(FAMILY_ID, FAMILY_NAME);
        familyStore.addMemberWithRole(FAMILY_ID, ADMIN_USER_ID, Role.ADMIN);
        familyStore.addMemberWithRole(FAMILY_ID, VIEWER_USER_ID, Role.VIEWER);

        personStore.addPerson(new Person(PERSON1_ID, FAMILY_ID, "John", "Doe"));
        personStore.addPerson(new Person(PERSON2_ID, FAMILY_ID, "Jane", "Doe"));
        personStore.addPerson(new Person(PERSON3_ID, FAMILY_ID, "Alice", "Smith"));
    }

    // Test 1: 401 - no auth on POST unions
    @Test
    void createUnion_noAuth_returns401() throws Exception {
        String body = """
            {"partner_a_id": "%s", "partner_b_id": "%s"}
            """.formatted(PERSON1_ID, PERSON2_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("authentication required"));
    }

    // Test 2: 404 - non-member on POST unions (B1)
    @Test
    void createUnion_nonMemberUser_returns404() throws Exception {
        String body = """
            {"partner_a_id": "%s", "partner_b_id": "%s"}
            """.formatted(PERSON1_ID, PERSON2_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions")
                        .header(AUTH_HEADER, bearerToken(NON_MEMBER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    // Test 3: 403 - viewer on POST unions (B3)
    @Test
    void createUnion_viewerUser_returns403() throws Exception {
        String body = """
            {"partner_a_id": "%s", "partner_b_id": "%s"}
            """.formatted(PERSON1_ID, PERSON2_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("write access required"));
    }

    // Test 4: 201 - admin create union success
    @Test
    void createUnion_adminValid_returns201() throws Exception {
        String body = """
            {"partner_a_id": "%s", "partner_b_id": "%s", "started_at": "2020-06-15"}
            """.formatted(PERSON1_ID, PERSON2_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.partner_ids[0]").value(PERSON1_ID.toString()))
                .andExpect(jsonPath("$.partner_ids[1]").value(PERSON2_ID.toString()))
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.started_at").value("2020-06-15"))
                .andExpect(jsonPath("$.ended_at").doesNotExist())
                .andExpect(jsonPath("$.ended_reason").doesNotExist());
    }

    // Test 5: 422 - self-union reject
    @Test
    void createUnion_selfUnion_returns422() throws Exception {
        String body = """
            {"partner_a_id": "%s", "partner_b_id": "%s"}
            """.formatted(PERSON1_ID, PERSON1_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("cannot create union with oneself"));
    }

    // Test 6: 404 - partner not in family
    @Test
    void createUnion_partnerNotInFamily_returns404() throws Exception {
        String body = """
            {"partner_a_id": "%s", "partner_b_id": "%s"}
            """.formatted(PERSON1_ID, PERSON_NOT_IN_FAMILY_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    // Test 7: 422 - second active union for same person rejected
    @Test
    void createUnion_secondActiveForSamePerson_returns422() throws Exception {
        String firstUnion = """
            {"partner_a_id": "%s", "partner_b_id": "%s"}
            """.formatted(PERSON1_ID, PERSON2_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstUnion))
                .andExpect(status().isCreated());

        String secondUnion = """
            {"partner_a_id": "%s", "partner_b_id": "%s"}
            """.formatted(PERSON1_ID, PERSON3_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondUnion))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("partner already has an active union; end existing union first"));
    }

    // Test 8: 200 - end union success
    @Test
    void endUnion_adminValid_returns200() throws Exception {
        UUID unionId = UUID.randomUUID();
        Union union = new Union(unionId, FAMILY_ID, PERSON1_ID, PERSON2_ID,
                MarriageStatus.ACTIVE, "2020-06-15", null, null);
        unionStore.addUnion(union);

        String body = """
            {"ended_reason": "divorced", "ended_at": "2023-01-01"}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions/" + unionId + "/end")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(unionId.toString()))
                .andExpect(jsonPath("$.status").value("divorced"))
                .andExpect(jsonPath("$.ended_at").value("2023-01-01"))
                .andExpect(jsonPath("$.ended_reason").value("divorced"));
    }

    // Test 9: 404 - end union not found
    @Test
    void endUnion_unionNotFound_returns404() throws Exception {
        UUID nonExistentUnionId = UUID.randomUUID();

        String body = """
            {"ended_reason": "divorced"}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions/" + nonExistentUnionId + "/end")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    // Test 10: 422 - end already ended union
    @Test
    void endUnion_alreadyEnded_returns422() throws Exception {
        UUID unionId = UUID.randomUUID();
        Union union = new Union(unionId, FAMILY_ID, PERSON1_ID, PERSON2_ID,
                MarriageStatus.DIVORCED, "2020-06-15", "2023-01-01", "divorced");
        unionStore.addUnion(union);

        String body = """
            {"ended_reason": "widowed"}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions/" + unionId + "/end")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("union has already ended"));
    }

    // Test 11: 401 - no auth on end union
    @Test
    void endUnion_noAuth_returns401() throws Exception {
        UUID unionId = UUID.randomUUID();
        Union union = new Union(unionId, FAMILY_ID, PERSON1_ID, PERSON2_ID,
                MarriageStatus.ACTIVE, "2020-06-15", null, null);
        unionStore.addUnion(union);

        String body = """
            {"ended_reason": "divorced"}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions/" + unionId + "/end")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("authentication required"));
    }

    // Test 12: 403 - viewer on end union
    @Test
    void endUnion_viewerUser_returns403() throws Exception {
        UUID unionId = UUID.randomUUID();
        Union union = new Union(unionId, FAMILY_ID, PERSON1_ID, PERSON2_ID,
                MarriageStatus.ACTIVE, "2020-06-15", null, null);
        unionStore.addUnion(union);

        String body = """
            {"ended_reason": "divorced"}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions/" + unionId + "/end")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("write access required"));
    }

    // Test 13: 400 - invalid partner_a_id UUID
    @Test
    void createUnion_invalidPartnerAIdUuid_returns400() throws Exception {
        String body = """
            {"partner_a_id": "not-a-uuid", "partner_b_id": "%s"}
            """.formatted(PERSON2_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid partner_a_id"));
    }

    // Test 14: 200 - end union with widowed reason
    @Test
    void endUnion_widowedReason_returnsWidowedStatus() throws Exception {
        UUID unionId = UUID.randomUUID();
        Union union = new Union(unionId, FAMILY_ID, PERSON1_ID, PERSON2_ID,
                MarriageStatus.ACTIVE, "2020-06-15", null, null);
        unionStore.addUnion(union);

        String body = """
            {"ended_reason": "widowed", "ended_at": "2023-05-20"}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions/" + unionId + "/end")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("widowed"))
                .andExpect(jsonPath("$.ended_reason").value("widowed"));
    }

    // Test 15: 200 - end union with default ended reason
    @Test
    void endUnion_noReason_returnsEndedStatus() throws Exception {
        UUID unionId = UUID.randomUUID();
        Union union = new Union(unionId, FAMILY_ID, PERSON1_ID, PERSON2_ID,
                MarriageStatus.ACTIVE, "2020-06-15", null, null);
        unionStore.addUnion(union);

        String body = "{}";

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions/" + unionId + "/end")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ended"))
                .andExpect(jsonPath("$.ended_reason").value("ended"));
    }

    // Test 16: 201 - create union without started_at
    @Test
    void createUnion_withoutStartedAt_returns201() throws Exception {
        String body = """
            {"partner_a_id": "%s", "partner_b_id": "%s"}
            """.formatted(PERSON1_ID, PERSON2_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.started_at").doesNotExist());
    }

    // Test 17: remarriage after ending previous union
    @Test
    void createUnion_afterEndingPrevious_returns201() throws Exception {
        String firstUnion = """
            {"partner_a_id": "%s", "partner_b_id": "%s"}
            """.formatted(PERSON1_ID, PERSON2_ID);

        var createResult = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstUnion))
                .andExpect(status().isCreated())
                .andReturn();

        String unionId = com.fasterxml.jackson.databind.ObjectMapper.class.newInstance()
                .readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        String endBody = """
            {"ended_reason": "divorced"}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions/" + unionId + "/end")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(endBody))
                .andExpect(status().isOk());

        String secondUnion = """
            {"partner_a_id": "%s", "partner_b_id": "%s"}
            """.formatted(PERSON1_ID, PERSON3_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondUnion))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("active"));
    }
}
