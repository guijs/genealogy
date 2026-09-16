package com.genealogy.web;

import com.genealogy.domain.family.Role;
import com.genealogy.domain.person.Person;
import com.genealogy.domain.projection.Gender;
import com.genealogy.domain.projection.ProjectionPerson;
import com.genealogy.mapper.PersonMapper;
import com.genealogy.mapper.StoryMapper;
import com.genealogy.store.FamilyStore;
import com.genealogy.store.PersonStore;
import com.genealogy.store.ProjectionStore;
import com.genealogy.store.StoryStore;
import com.genealogy.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StoryControllerTest extends BaseIntegrationTest {

    private static final String AUTH_HEADER = "Authorization";

    @Autowired
    private FamilyStore familyStore;

    @Autowired
    private PersonStore personStore;

    @Autowired
    private ProjectionStore projectionStore;

    @Autowired
    private StoryStore storyStore;

    @Autowired
    private PersonMapper personMapper;

    @Autowired
    private StoryMapper storyMapper;

    private static final UUID FAMILY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID EDITOR_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID VIEWER_USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID NON_MEMBER_USER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID PERSON_ID_1 = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID PERSON_ID_2 = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID PERSON_ID_HIDDEN = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final String FAMILY_NAME = "Test Family";

    @BeforeEach
    void setUp() {
        cleanAllData();

        familyStore.createFamily(FAMILY_ID, FAMILY_NAME);
        familyStore.addMemberWithRole(FAMILY_ID, ADMIN_USER_ID, Role.ADMIN);
        familyStore.addMemberWithRole(FAMILY_ID, EDITOR_USER_ID, Role.EDITOR);
        familyStore.addMemberWithRole(FAMILY_ID, VIEWER_USER_ID, Role.VIEWER);

        personStore.addPerson(new Person(PERSON_ID_1, FAMILY_ID, "Person", "One"));
        projectionStore.createPerson(new ProjectionPerson(
                PERSON_ID_1, FAMILY_ID, "Person One", Gender.MALE, 1980, null, false));

        personStore.addPerson(new Person(PERSON_ID_2, FAMILY_ID, "Person", "Two"));
        projectionStore.createPerson(new ProjectionPerson(
                PERSON_ID_2, FAMILY_ID, "Person Two", Gender.FEMALE, 1985, null, false));

        personStore.addPerson(new Person(PERSON_ID_HIDDEN, FAMILY_ID, "Hidden", "Person"));
        projectionStore.createPerson(new ProjectionPerson(
                PERSON_ID_HIDDEN, FAMILY_ID, "Hidden Person", Gender.MALE, 1970, null, true));
    }

    // AP-S1: editor create person-linked
    @Test
    void createStory_editorWithSinglePerson_returns201() throws Exception {
        String body = """
            {
                "title": "My Story",
                "body": "This is the story body",
                "narrative_time": "2020-01-15",
                "person_ids": ["%s"]
            }
            """.formatted(PERSON_ID_1);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.family_id").value(FAMILY_ID.toString()))
                .andExpect(jsonPath("$.title").value("My Story"))
                .andExpect(jsonPath("$.body").value("This is the story body"))
                .andExpect(jsonPath("$.narrative_time").value("2020-01-15"))
                .andExpect(jsonPath("$.person_ids", hasSize(1)))
                .andExpect(jsonPath("$.person_ids[0]").value(PERSON_ID_1.toString()))
                .andExpect(jsonPath("$.created_by").value(EDITOR_USER_ID.toString()))
                .andExpect(jsonPath("$.updated_by").value(EDITOR_USER_ID.toString()))
                .andExpect(jsonPath("$.version").value(1));
    }

    // AP-S2: editor create multi-person
    @Test
    void createStory_editorWithMultiplePersons_returns201() throws Exception {
        String body = """
            {
                "title": "Family Gathering",
                "body": "A story about multiple people",
                "person_ids": ["%s", "%s"]
            }
            """.formatted(PERSON_ID_1, PERSON_ID_2);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.person_ids", hasSize(2)))
                .andExpect(jsonPath("$.person_ids", containsInAnyOrder(
                        PERSON_ID_1.toString(), PERSON_ID_2.toString())));
    }

    // AP-S3: editor create family-scoped (0 persons)
    @Test
    void createStory_editorFamilyScoped_returns201() throws Exception {
        String body = """
            {
                "title": "Family History",
                "body": "General family story without specific persons"
            }
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.person_ids", hasSize(0)));
    }

    // AP-S4: viewer create → rejected
    @Test
    void createStory_viewer_returns403() throws Exception {
        String body = """
            {
                "title": "My Story",
                "body": "This is the story body"
            }
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("write access required"));
    }

    // AP-S5: viewer read visible stories
    @Test
    void listStories_viewer_returnsVisibleStories() throws Exception {
        String createBody = """
            {
                "title": "Visible Story",
                "body": "A story the viewer can see",
                "person_ids": ["%s"]
            }
            """.formatted(PERSON_ID_1);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stories", hasSize(1)))
                .andExpect(jsonPath("$.stories[0].title").value("Visible Story"));
    }

    // AP-S6: invalid mount → 409
    @Test
    void createStory_personNotInFamily_returns409() throws Exception {
        UUID otherFamilyPerson = UUID.randomUUID();
        String body = """
            {
                "body": "Story with invalid person",
                "person_ids": ["%s"]
            }
            """.formatted(otherFamilyPerson);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString("not found in family")));
    }

    // AP-S7: Hidden person - viewer cannot see
    @Test
    void listStories_viewer_omitsStoriesWithHiddenPerson() throws Exception {
        String createBody = """
            {
                "title": "Story with Hidden Person",
                "body": "This story links to a hidden person",
                "person_ids": ["%s"]
            }
            """.formatted(PERSON_ID_HIDDEN);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stories", hasSize(0)));
    }

    @Test
    void getStory_viewer_hiddenPersonLinked_returns404() throws Exception {
        String createBody = """
            {
                "title": "Hidden Story",
                "body": "This story is linked to a hidden person",
                "person_ids": ["%s"]
            }
            """.formatted(PERSON_ID_HIDDEN);

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String storyId = com.fasterxml.jackson.databind.ObjectMapper
                .class.getConstructor().newInstance()
                .readTree(result).get("id").asText();

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId)
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isNotFound());
    }

    // AP-S8: admin/editor can see stories with hidden persons
    @Test
    void listStories_adminEditor_canSeeStoriesWithHiddenPerson() throws Exception {
        String createBody = """
            {
                "title": "Story with Hidden Person",
                "body": "This story links to a hidden person",
                "person_ids": ["%s"]
            }
            """.formatted(PERSON_ID_HIDDEN);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stories", hasSize(1)));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stories", hasSize(1)));
    }

    // AP-S9: version conflict → 409 with current story
    @Test
    void updateStory_versionConflict_returns409WithCurrentStory() throws Exception {
        String createBody = """
            {
                "title": "Original",
                "body": "Original body"
            }
            """;

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String storyId = mapper.readTree(result).get("id").asText();

        String updateBody1 = """
            {
                "title": "Updated by Editor 1",
                "body": "Updated body 1",
                "version": 1
            }
            """;

        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId)
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        String updateBody2 = """
            {
                "title": "Updated by Admin",
                "body": "Updated body 2",
                "version": 1
            }
            """;

        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId)
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.title").value("Updated by Editor 1"));
    }

    // AP-S10: body too long → rejected
    @Test
    void createStory_bodyTooLong_returns400() throws Exception {
        String longBody = "x".repeat(10001);
        String body = """
            {
                "title": "Long Story",
                "body": "%s"
            }
            """.formatted(longBody);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("exceeds maximum length")));
    }

    // AP-S11: non-member → not visible
    @Test
    void listStories_nonMember_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(NON_MEMBER_USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    @Test
    void createStory_nonMember_returns404() throws Exception {
        String body = """
            {
                "body": "Test story"
            }
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(NON_MEMBER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    // AP-S13: updated_by reflects last editor
    @Test
    void updateStory_updatesUpdatedBy() throws Exception {
        String createBody = """
            {
                "body": "Original story"
            }
            """;

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created_by").value(EDITOR_USER_ID.toString()))
                .andExpect(jsonPath("$.updated_by").value(EDITOR_USER_ID.toString()))
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String storyId = mapper.readTree(result).get("id").asText();

        String updateBody = """
            {
                "body": "Updated by admin",
                "version": 1
            }
            """;

        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId)
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created_by").value(EDITOR_USER_ID.toString()))
                .andExpect(jsonPath("$.updated_by").value(ADMIN_USER_ID.toString()));
    }

    @Test
    void deleteStory_editor_returns204() throws Exception {
        String createBody = """
            {
                "body": "Story to delete"
            }
            """;

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String storyId = mapper.readTree(result).get("id").asText();

        String deleteBody = """
            {
                "version": 1
            }
            """;

        mockMvc.perform(delete("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId)
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteBody))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId)
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteStory_viewer_returns403() throws Exception {
        String createBody = """
            {
                "body": "Story to delete"
            }
            """;

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String storyId = mapper.readTree(result).get("id").asText();

        String deleteBody = """
            {
                "version": 1
            }
            """;

        mockMvc.perform(delete("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId)
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("write access required"));
    }

    @Test
    void deleteStory_versionConflict_returns409() throws Exception {
        String createBody = """
            {
                "body": "Story to delete"
            }
            """;

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String storyId = mapper.readTree(result).get("id").asText();

        String updateBody = """
            {
                "body": "Updated story",
                "version": 1
            }
            """;

        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId)
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        String deleteBody = """
            {
                "version": 1
            }
            """;

        mockMvc.perform(delete("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId)
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void createStory_emptyBody_returns400() throws Exception {
        String body = """
            {
                "title": "Story with no body"
            }
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("body is required"));
    }

    @Test
    void createStory_blankBody_returns400() throws Exception {
        String body = """
            {
                "title": "Story",
                "body": "   "
            }
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("body is required"));
    }

    @Test
    void updateStory_missingVersion_returns400() throws Exception {
        String createBody = """
            {
                "body": "Original story"
            }
            """;

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String storyId = mapper.readTree(result).get("id").asText();

        String updateBody = """
            {
                "body": "Updated story"
            }
            """;

        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId)
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("version is required for updates"));
    }

    @Test
    void listStories_filterByPerson_returnsOnlyMatchingStories() throws Exception {
        String storyWithPerson1 = """
            {
                "title": "Person 1 Story",
                "body": "About person 1",
                "person_ids": ["%s"]
            }
            """.formatted(PERSON_ID_1);

        String storyWithPerson2 = """
            {
                "title": "Person 2 Story",
                "body": "About person 2",
                "person_ids": ["%s"]
            }
            """.formatted(PERSON_ID_2);

        String familyStory = """
            {
                "title": "Family Story",
                "body": "About the family"
            }
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(storyWithPerson1))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(storyWithPerson2))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(familyStory))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID))
                        .param("personId", PERSON_ID_1.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stories", hasSize(1)))
                .andExpect(jsonPath("$.stories[0].title").value("Person 1 Story"));
    }

    @Test
    void createStory_duplicatePersonIds_returns409() throws Exception {
        String body = """
            {
                "body": "Story with duplicates",
                "person_ids": ["%s", "%s"]
            }
            """.formatted(PERSON_ID_1, PERSON_ID_1);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("duplicate person_ids not allowed"));
    }

    @Test
    void createStory_admin_returns201() throws Exception {
        String body = """
            {
                "body": "Admin story"
            }
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created_by").value(ADMIN_USER_ID.toString()));
    }

    @Test
    void getStory_returns200() throws Exception {
        String createBody = """
            {
                "title": "Specific Story",
                "body": "Story body"
            }
            """;

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String storyId = mapper.readTree(result).get("id").asText();

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId)
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(storyId))
                .andExpect(jsonPath("$.title").value("Specific Story"));
    }

    @Test
    void getStory_notFound_returns404() throws Exception {
        UUID unknownStoryId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories/" + unknownStoryId)
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createStory_noAuth_returns401() throws Exception {
        String body = """
            {
                "body": "Test story"
            }
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateStory_viewer_returns403() throws Exception {
        String createBody = """
            {
                "body": "Original story"
            }
            """;

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String storyId = mapper.readTree(result).get("id").asText();

        String updateBody = """
            {
                "body": "Updated story",
                "version": 1
            }
            """;

        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId)
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("write access required"));
    }

    @Test
    void createStory_withMarkdown_preservesFormatting() throws Exception {
        String markdownBody = "# Heading\n\n**Bold text** and *italic*\n\n- Item 1\n- Item 2";
        String body = """
            {
                "title": "Markdown Story",
                "body": "# Heading\\n\\n**Bold text** and *italic*\\n\\n- Item 1\\n- Item 2"
            }
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value(containsString("# Heading")))
                .andExpect(jsonPath("$.body").value(containsString("**Bold text**")));
    }

    @Test
    void createStory_bodyAtMaxLength_returns201() throws Exception {
        String maxBody = "x".repeat(10000);
        String body = """
            {
                "body": "%s"
            }
            """.formatted(maxBody);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value(maxBody));
    }

    @Test
    void personHardDelete_unbindsFromStory_becomesFamilyScoped() throws Exception {
        UUID tempPersonId = UUID.randomUUID();
        personStore.addPerson(new Person(tempPersonId, FAMILY_ID, "Temp", "Person"));
        projectionStore.createPerson(new ProjectionPerson(
                tempPersonId, FAMILY_ID, "Temp Person", Gender.MALE, 1990, null, false));

        String createBody = """
            {
                "title": "Story about temp person",
                "body": "This story is linked to a person who will be deleted",
                "person_ids": ["%s"]
            }
            """.formatted(tempPersonId);

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.person_ids", hasSize(1)))
                .andExpect(jsonPath("$.person_ids[0]").value(tempPersonId.toString()))
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String storyId = mapper.readTree(result).get("id").asText();

        personMapper.deleteById(tempPersonId);

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId)
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(storyId))
                .andExpect(jsonPath("$.title").value("Story about temp person"))
                .andExpect(jsonPath("$.body").value("This story is linked to a person who will be deleted"))
                .andExpect(jsonPath("$.person_ids", hasSize(0)));
    }

    @Test
    void personHardDelete_multiplePersons_unbindsOnlyDeleted() throws Exception {
        UUID tempPersonId = UUID.randomUUID();
        personStore.addPerson(new Person(tempPersonId, FAMILY_ID, "Temp", "Person"));
        projectionStore.createPerson(new ProjectionPerson(
                tempPersonId, FAMILY_ID, "Temp Person", Gender.MALE, 1990, null, false));

        String createBody = """
            {
                "title": "Story about two people",
                "body": "One person will be deleted",
                "person_ids": ["%s", "%s"]
            }
            """.formatted(PERSON_ID_1, tempPersonId);

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.person_ids", hasSize(2)))
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String storyId = mapper.readTree(result).get("id").asText();

        personMapper.deleteById(tempPersonId);

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId)
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(storyId))
                .andExpect(jsonPath("$.person_ids", hasSize(1)))
                .andExpect(jsonPath("$.person_ids[0]").value(PERSON_ID_1.toString()));
    }

    @Test
    void updateStory_atomicVersionConflict_sqlGatesOnVersion() throws Exception {
        String createBody = """
            {
                "title": "Original Title",
                "body": "Original body content"
            }
            """;

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String storyId = mapper.readTree(result).get("id").asText();
        UUID storyUuid = UUID.fromString(storyId);

        int rowsAffected = storyMapper.updateStory(
                storyUuid,
                "Concurrent Update Title",
                "Concurrent update body - this simulates another process updating",
                null,
                ADMIN_USER_ID,
                1
        );
        assert rowsAffected == 1 : "Concurrent update should succeed";

        String updateBody = """
            {
                "title": "Stale Update Attempt",
                "body": "This update uses stale version 1",
                "version": 1
            }
            """;

        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId)
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.title").value("Concurrent Update Title"))
                .andExpect(jsonPath("$.body").value("Concurrent update body - this simulates another process updating"));
    }

    @Test
    void deleteStory_atomicVersionConflict_sqlGatesOnVersion() throws Exception {
        String createBody = """
            {
                "title": "Story to Delete",
                "body": "Original body"
            }
            """;

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String storyId = mapper.readTree(result).get("id").asText();
        UUID storyUuid = UUID.fromString(storyId);

        int rowsAffected = storyMapper.updateStory(
                storyUuid,
                "Updated Before Delete Attempt",
                "Body updated by concurrent process",
                null,
                ADMIN_USER_ID,
                1
        );
        assert rowsAffected == 1 : "Concurrent update should succeed";

        String deleteBody = """
            {
                "version": 1
            }
            """;

        mockMvc.perform(delete("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId)
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.title").value("Updated Before Delete Attempt"))
                .andExpect(jsonPath("$.body").value("Body updated by concurrent process"));

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId)
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
    }
}
