package com.genealogy.web;

import com.genealogy.domain.family.Role;
import com.genealogy.domain.person.Person;
import com.genealogy.domain.projection.MarriageStatus;
import com.genealogy.domain.union.Union;
import com.genealogy.service.GraphService;
import com.genealogy.service.UnionService;
import com.genealogy.store.*;
import com.genealogy.web.filter.AuthFilter;
import com.genealogy.web.filter.FamilyMembershipFilter;
import com.genealogy.web.filter.WriteAccessFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({UnionController.class, GraphController.class})
@Import({FamilyStore.class, PersonStore.class, UnionStore.class, ProjectionStore.class,
        UnionService.class, GraphService.class,
        AuthFilter.class, FamilyMembershipFilter.class, WriteAccessFilter.class})
class UnionGraphIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
    private static final UUID PERSON1_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID PERSON2_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID PERSON3_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final String FAMILY_NAME = "Test Family";

    @BeforeEach
    void setUp() {
        familyStore.clear();
        personStore.clear();
        unionStore.clear();
        projectionStore.clear();

        familyStore.createFamily(FAMILY_ID, FAMILY_NAME);
        familyStore.addMemberWithRole(FAMILY_ID, ADMIN_USER_ID, Role.ADMIN);
        familyStore.addMemberWithRole(FAMILY_ID, VIEWER_USER_ID, Role.VIEWER);

        personStore.addPerson(new Person(PERSON1_ID, FAMILY_ID, "John", "Doe"));
        personStore.addPerson(new Person(PERSON2_ID, FAMILY_ID, "Jane", "Doe"));
        personStore.addPerson(new Person(PERSON3_ID, FAMILY_ID, "Alice", "Smith"));
    }

    @Test
    void createUnionThenReadGraph_graphContainsActiveMarriage() throws Exception {
        String body = """
            {"partner_a_id": "%s", "partner_b_id": "%s", "started_at": "2020-06-15"}
            """.formatted(PERSON1_ID, PERSON2_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("active"));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .param("rootPersonId", PERSON1_ID.toString())
                        .param("depth", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.familyId").value(FAMILY_ID.toString()))
                .andExpect(jsonPath("$.persons", hasSize(2)))
                .andExpect(jsonPath("$.persons[*].id", hasItem(PERSON1_ID.toString())))
                .andExpect(jsonPath("$.persons[*].id", hasItem(PERSON2_ID.toString())))
                .andExpect(jsonPath("$.marriages", hasSize(1)))
                .andExpect(jsonPath("$.marriages[0].status").value("active"))
                .andExpect(jsonPath("$.marriages[0].startedAt").value("2020-06-15"))
                .andExpect(jsonPath("$.marriages[0].partnerIds", hasSize(2)))
                .andExpect(jsonPath("$.marriages[0].partnerIds", hasItem(PERSON1_ID.toString())))
                .andExpect(jsonPath("$.marriages[0].partnerIds", hasItem(PERSON2_ID.toString())));
    }

    @Test
    void endUnionThenReadGraph_graphStillContainsEndedMarriage() throws Exception {
        String createBody = """
            {"partner_a_id": "%s", "partner_b_id": "%s", "started_at": "2020-06-15"}
            """.formatted(PERSON1_ID, PERSON2_ID);

        var createResult = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();

        String unionId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        String endBody = """
            {"ended_reason": "divorced", "ended_at": "2023-01-01"}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions/" + unionId + "/end")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(endBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("divorced"));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .param("rootPersonId", PERSON1_ID.toString())
                        .param("depth", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marriages", hasSize(1)))
                .andExpect(jsonPath("$.marriages[0].status").value("divorced"))
                .andExpect(jsonPath("$.marriages[0].endedAt").value("2023-01-01"))
                .andExpect(jsonPath("$.marriages[0].endedReason").value("divorced"));
    }

    @Test
    void remarriageAfterDivorce_graphContainsBothMarriages() throws Exception {
        String firstUnion = """
            {"partner_a_id": "%s", "partner_b_id": "%s", "started_at": "2015-01-01"}
            """.formatted(PERSON1_ID, PERSON2_ID);

        var firstResult = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstUnion))
                .andExpect(status().isCreated())
                .andReturn();

        String firstUnionId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(firstResult.getResponse().getContentAsString())
                .get("id").asText();

        String endBody = """
            {"ended_reason": "divorced", "ended_at": "2020-01-01"}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions/" + firstUnionId + "/end")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(endBody))
                .andExpect(status().isOk());

        String secondUnion = """
            {"partner_a_id": "%s", "partner_b_id": "%s", "started_at": "2021-06-15"}
            """.formatted(PERSON1_ID, PERSON3_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondUnion))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .param("rootPersonId", PERSON1_ID.toString())
                        .param("depth", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons", hasSize(3)))
                .andExpect(jsonPath("$.marriages", hasSize(2)))
                .andExpect(jsonPath("$.marriages[?(@.status == 'active')]", hasSize(1)))
                .andExpect(jsonPath("$.marriages[?(@.status == 'divorced')]", hasSize(1)));
    }

    @Test
    void viewerCanReadGraphWithMarriages() throws Exception {
        String body = """
            {"partner_a_id": "%s", "partner_b_id": "%s"}
            """.formatted(PERSON1_ID, PERSON2_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", VIEWER_USER_ID.toString())
                        .param("rootPersonId", PERSON1_ID.toString())
                        .param("depth", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marriages", hasSize(1)))
                .andExpect(jsonPath("$.marriages[0].status").value("active"));
    }

    @Test
    void endUnionWithWidowed_statusIsWidowed() throws Exception {
        String createBody = """
            {"partner_a_id": "%s", "partner_b_id": "%s"}
            """.formatted(PERSON1_ID, PERSON2_ID);

        var createResult = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();

        String unionId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        String endBody = """
            {"ended_reason": "widowed", "ended_at": "2023-05-20"}
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions/" + unionId + "/end")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(endBody))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .param("rootPersonId", PERSON1_ID.toString())
                        .param("depth", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marriages", hasSize(1)))
                .andExpect(jsonPath("$.marriages[0].status").value("widowed"))
                .andExpect(jsonPath("$.marriages[0].endedReason").value("widowed"));
    }

    @Test
    void graphFromPartnerBPerspective_stillShowsMarriage() throws Exception {
        String body = """
            {"partner_a_id": "%s", "partner_b_id": "%s"}
            """.formatted(PERSON1_ID, PERSON2_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/unions")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/graph")
                        .header("X-User-Id", ADMIN_USER_ID.toString())
                        .param("rootPersonId", PERSON2_ID.toString())
                        .param("depth", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootPersonId").value(PERSON2_ID.toString()))
                .andExpect(jsonPath("$.persons", hasSize(2)))
                .andExpect(jsonPath("$.marriages", hasSize(1)));
    }
}
