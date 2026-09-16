package com.genealogy.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genealogy.domain.family.Role;
import com.genealogy.domain.person.Person;
import com.genealogy.domain.projection.Gender;
import com.genealogy.domain.projection.ProjectionPerson;
import com.genealogy.domain.story.Story;
import com.genealogy.mapper.StoryCommentMapper;
import com.genealogy.store.FamilyStore;
import com.genealogy.store.PersonStore;
import com.genealogy.store.ProjectionStore;
import com.genealogy.store.StoryStore;
import com.genealogy.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StoryCommentControllerTest extends BaseIntegrationTest {

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
    private StoryCommentMapper commentMapper;

    private static final UUID FAMILY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID EDITOR_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID VIEWER_USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID NON_MEMBER_USER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID PERSON_ID_1 = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID PERSON_ID_HIDDEN = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final String FAMILY_NAME = "Test Family";

    private UUID storyId;
    private UUID hiddenStoryId;

    private final ObjectMapper mapper = new ObjectMapper();

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

        personStore.addPerson(new Person(PERSON_ID_HIDDEN, FAMILY_ID, "Hidden", "Person"));
        projectionStore.createPerson(new ProjectionPerson(
                PERSON_ID_HIDDEN, FAMILY_ID, "Hidden Person", Gender.MALE, 1970, null, true));

        storyId = UUID.randomUUID();
        Story story = new Story(
                storyId,
                FAMILY_ID,
                "Test Story",
                "Test story body",
                LocalDate.now(),
                EDITOR_USER_ID,
                EDITOR_USER_ID,
                null,
                null,
                1,
                List.of(PERSON_ID_1)
        );
        storyStore.createStory(story);

        hiddenStoryId = UUID.randomUUID();
        Story hiddenStory = new Story(
                hiddenStoryId,
                FAMILY_ID,
                "Hidden Story",
                "Story linked to hidden person",
                LocalDate.now(),
                ADMIN_USER_ID,
                ADMIN_USER_ID,
                null,
                null,
                1,
                List.of(PERSON_ID_HIDDEN)
        );
        storyStore.createStory(hiddenStory);
    }

    // AP-C1: admin/editor 在可见故事下发评 → 成功
    @Test
    void apC1_createComment_admin_returns201() throws Exception {
        String body = """
            {
                "body": "Admin comment on story"
            }
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.story_id").value(storyId.toString()))
                .andExpect(jsonPath("$.author_user_id").value(ADMIN_USER_ID.toString()))
                .andExpect(jsonPath("$.body").value("Admin comment on story"))
                .andExpect(jsonPath("$.created_at").isNotEmpty())
                .andExpect(jsonPath("$.updated_at").isNotEmpty());
    }

    @Test
    void apC1_createComment_editor_returns201() throws Exception {
        String body = """
            {
                "body": "Editor comment on story"
            }
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author_user_id").value(EDITOR_USER_ID.toString()))
                .andExpect(jsonPath("$.body").value("Editor comment on story"));
    }

    // AP-C2: viewer 发评 → 拒绝
    @Test
    void apC2_createComment_viewer_returns403() throws Exception {
        String body = """
            {
                "body": "Viewer comment"
            }
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("write access required"));
    }

    // AP-C3: 非成员发评 → 拒绝
    @Test
    void apC3_createComment_nonMember_returns404() throws Exception {
        String body = """
            {
                "body": "Non-member comment"
            }
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(NON_MEMBER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    // AP-C4: 列表为单层；无回复入口 → API 无 parent
    @Test
    void apC4_listComments_returnsFlat_noParent() throws Exception {
        String body1 = """
            {
                "body": "First comment"
            }
            """;
        String body2 = """
            {
                "body": "Second comment"
            }
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body1))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments", hasSize(2)))
                .andExpect(jsonPath("$.comments[0].body").value("First comment"))
                .andExpect(jsonPath("$.comments[1].body").value("Second comment"))
                .andExpect(jsonPath("$.comments[0].parent_comment_id").doesNotExist())
                .andExpect(jsonPath("$.comments[1].parent_comment_id").doesNotExist());
    }

    // AP-C5: 作者改己评（带正确 updated_at）→ 成功
    @Test
    void apC5_updateComment_author_withCorrectUpdatedAt_returns200() throws Exception {
        String createBody = """
            {
                "body": "Original comment"
            }
            """;

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String commentId = mapper.readTree(result).get("id").asText();
        String updatedAt = mapper.readTree(result).get("updated_at").asText();

        String updateBody = """
            {
                "body": "Updated comment",
                "updated_at": "%s"
            }
            """.formatted(updatedAt);

        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + commentId)
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Updated comment"));
    }

    // AP-C6: 作者改己评（stale updated_at）→ 409 + 当前副本
    @Test
    void apC6_updateComment_author_withStaleUpdatedAt_returns409() throws Exception {
        String createBody = """
            {
                "body": "Original comment"
            }
            """;

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String commentId = mapper.readTree(result).get("id").asText();
        String originalUpdatedAt = mapper.readTree(result).get("updated_at").asText();

        String updateBody1 = """
            {
                "body": "First update",
                "updated_at": "%s"
            }
            """.formatted(originalUpdatedAt);

        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + commentId)
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("First update"));

        String updateBody2 = """
            {
                "body": "Stale update attempt",
                "updated_at": "%s"
            }
            """.formatted(originalUpdatedAt);

        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + commentId)
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.body").value("First update"))
                .andExpect(jsonPath("$.updated_at").isNotEmpty());
    }

    // AP-C7: 改他人评 → 拒绝
    @Test
    void apC7_updateComment_nonAuthor_returns403() throws Exception {
        String createBody = """
            {
                "body": "Editor's comment"
            }
            """;

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String commentId = mapper.readTree(result).get("id").asText();
        String updatedAt = mapper.readTree(result).get("updated_at").asText();

        String updateBody = """
            {
                "body": "Admin trying to edit",
                "updated_at": "%s"
            }
            """.formatted(updatedAt);

        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + commentId)
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("only author can edit comment"));
    }

    // AP-C8: 作者删己评 → 成功
    @Test
    void apC8_deleteComment_author_returns204() throws Exception {
        String createBody = """
            {
                "body": "Comment to delete"
            }
            """;

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String commentId = mapper.readTree(result).get("id").asText();

        mockMvc.perform(delete("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + commentId)
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + commentId)
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID)))
                .andExpect(status().isNotFound());
    }

    // AP-C9: admin 删他人评 → 成功
    @Test
    void apC9_deleteComment_admin_deleteOther_returns204() throws Exception {
        String createBody = """
            {
                "body": "Editor's comment"
            }
            """;

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String commentId = mapper.readTree(result).get("id").asText();

        mockMvc.perform(delete("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + commentId)
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID)))
                .andExpect(status().isNoContent());
    }

    // AP-C10: editor 删他人评 → 拒绝
    @Test
    void apC10_deleteComment_editor_deleteOther_returns403() throws Exception {
        String createBody = """
            {
                "body": "Admin's comment"
            }
            """;

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String commentId = mapper.readTree(result).get("id").asText();

        mockMvc.perform(delete("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + commentId)
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("only author or admin can delete comments"));
    }

    // AP-C11: 故事 Hidden 对 viewer 不可见 → 无评论列表/入口
    @Test
    void apC11_listComments_hiddenStory_viewer_returns404() throws Exception {
        String createBody = """
            {
                "body": "Comment on hidden story"
            }
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + hiddenStoryId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories/" + hiddenStoryId + "/comments")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    void apC11_listComments_hiddenStory_admin_returns200() throws Exception {
        String createBody = """
            {
                "body": "Comment on hidden story"
            }
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + hiddenStoryId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories/" + hiddenStoryId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments", hasSize(1)));
    }

    // AP-C12: 正文 >1000 → 拒绝
    @Test
    void apC12_createComment_bodyTooLong_returns400() throws Exception {
        String longBody = "x".repeat(1001);
        String body = """
            {
                "body": "%s"
            }
            """.formatted(longBody);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("exceeds maximum length")));
    }

    @Test
    void apC12_createComment_bodyAtMaxLength_returns201() throws Exception {
        String maxBody = "x".repeat(1000);
        String body = """
            {
                "body": "%s"
            }
            """.formatted(maxBody);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value(maxBody));
    }

    // Additional tests

    // Empty body - rejected
    @Test
    void createComment_emptyBody_returns400() throws Exception {
        String body = """
            {
                "body": ""
            }
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("body is required"));
    }

    // Missing updated_at on update - rejected
    @Test
    void updateComment_missingUpdatedAt_returns400() throws Exception {
        String createBody = """
            {
                "body": "Original comment"
            }
            """;

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String commentId = mapper.readTree(result).get("id").asText();

        String updateBody = """
            {
                "body": "Updated comment"
            }
            """;

        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + commentId)
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("updated_at is required for updates"));
    }

    // viewer can read comments
    @Test
    void listComments_viewer_returns200() throws Exception {
        String createBody = """
            {
                "body": "A comment"
            }
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments", hasSize(1)));
    }

    // viewer cannot delete any comment
    @Test
    void deleteComment_viewer_returns403() throws Exception {
        String createBody = """
            {
                "body": "Admin comment"
            }
            """;

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String commentId = mapper.readTree(result).get("id").asText();

        mockMvc.perform(delete("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + commentId)
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isForbidden());
    }

    // Story not found
    @Test
    void listComments_storyNotFound_returns404() throws Exception {
        UUID unknownStoryId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories/" + unknownStoryId + "/comments")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isNotFound());
    }

    // No auth - 401
    @Test
    void listComments_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments"))
                .andExpect(status().isUnauthorized());
    }

    // Get single comment
    @Test
    void getComment_returns200() throws Exception {
        String createBody = """
            {
                "body": "A comment"
            }
            """;

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String commentId = mapper.readTree(result).get("id").asText();

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + commentId)
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(commentId))
                .andExpect(jsonPath("$.body").value("A comment"));
    }

    // Comment not found
    @Test
    void getComment_notFound_returns404() throws Exception {
        UUID unknownCommentId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + unknownCommentId)
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isNotFound());
    }

    // CASCADE delete: story hard-delete should delete all comments
    @Test
    void storyHardDelete_cascadeDeletesComments() throws Exception {
        UUID tempStoryId = UUID.randomUUID();
        Story tempStory = new Story(
                tempStoryId,
                FAMILY_ID,
                "Temp Story",
                "Story to be deleted",
                LocalDate.now(),
                ADMIN_USER_ID,
                ADMIN_USER_ID,
                null,
                null,
                1,
                List.of()
        );
        storyStore.createStory(tempStory);

        String body1 = """
            {
                "body": "Comment 1"
            }
            """;
        String body2 = """
            {
                "body": "Comment 2"
            }
            """;

        String result1 = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + tempStoryId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body1))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + tempStoryId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2))
                .andExpect(status().isCreated());

        String commentId = mapper.readTree(result1).get("id").asText();

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories/" + tempStoryId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments", hasSize(2)));

        String deleteBody = """
            {
                "version": 1
            }
            """;

        mockMvc.perform(delete("/api/v1/families/" + FAMILY_ID + "/stories/" + tempStoryId)
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteBody))
                .andExpect(status().isNoContent());

        StoryCommentMapper.CommentRow deletedComment = commentMapper.findById(UUID.fromString(commentId));
        assert deletedComment == null : "Comment should be cascade deleted with story";
    }

    // Chronological ordering (created_at ASC)
    @Test
    void listComments_orderedChronologically() throws Exception {
        String body1 = """
            {
                "body": "First comment"
            }
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body1))
                .andExpect(status().isCreated());

        Thread.sleep(10);

        String body2 = """
            {
                "body": "Second comment"
            }
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2))
                .andExpect(status().isCreated());

        Thread.sleep(10);

        String body3 = """
            {
                "body": "Third comment"
            }
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body3))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments", hasSize(3)))
                .andExpect(jsonPath("$.comments[0].body").value("First comment"))
                .andExpect(jsonPath("$.comments[1].body").value("Second comment"))
                .andExpect(jsonPath("$.comments[2].body").value("Third comment"));
    }

    // ====================
    // MENTIONS TESTS (P1 MVP v0.2)
    // ====================

    // AP-M1: editor 键入 @ 选本家族当前成员 User 并发评 → 成功；mentions 含该 user_id
    @Test
    void apM1_createComment_withValidMention_returns201() throws Exception {
        String body = """
            {
                "body": "Hello @Editor, check this out!",
                "mentions": [
                    {
                        "user_id": "%s",
                        "display_name_snapshot": "Editor User"
                    }
                ]
            }
            """.formatted(EDITOR_USER_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("Hello @Editor, check this out!"))
                .andExpect(jsonPath("$.mentions", hasSize(1)))
                .andExpect(jsonPath("$.mentions[0].user_id").value(EDITOR_USER_ID.toString()))
                .andExpect(jsonPath("$.mentions[0].display_name_snapshot").value("Editor User"))
                .andExpect(jsonPath("$.mentions[0].status").value("active"));
    }

    // AP-M2: viewer 尝试带 mention 发评 → 拒绝
    @Test
    void apM2_createComment_viewerWithMention_returns403() throws Exception {
        String body = """
            {
                "body": "Hello @Admin!",
                "mentions": [
                    {
                        "user_id": "%s",
                        "display_name_snapshot": "Admin User"
                    }
                ]
            }
            """.formatted(ADMIN_USER_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // AP-M3: 提交已退出 / 非本家族 / 非 User → 400
    @Test
    void apM3_createComment_withNonMemberMention_returns400() throws Exception {
        String body = """
            {
                "body": "Hello @NonMember!",
                "mentions": [
                    {
                        "user_id": "%s",
                        "display_name_snapshot": "Non Member"
                    }
                ]
            }
            """.formatted(NON_MEMBER_USER_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("mentioned user is not a current family member"));
    }

    @Test
    void apM3_createComment_withRandomUUID_returns400() throws Exception {
        UUID randomUUID = UUID.randomUUID();
        String body = """
            {
                "body": "Hello @Random!",
                "mentions": [
                    {
                        "user_id": "%s",
                        "display_name_snapshot": "Random Person"
                    }
                ]
            }
            """.formatted(randomUUID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("mentioned user is not a current family member"));
    }

    // AP-M4: 手工纯文本 @张三 无选人 → 无结构化 mention
    @Test
    void apM4_createComment_plainTextAtMention_noStructuredMention() throws Exception {
        String body = """
            {
                "body": "Hello @张三, check this out!"
            }
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("Hello @张三, check this out!"))
                .andExpect(jsonPath("$.mentions").doesNotExist());
    }

    @Test
    void apM4_createComment_emptyMentionsList_noStructuredMention() throws Exception {
        String body = """
            {
                "body": "Hello @张三, check this out!",
                "mentions": []
            }
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("Hello @张三, check this out!"))
                .andExpect(jsonPath("$.mentions").doesNotExist());
    }

    // AP-M5: 读评：被 @ 人仍为当前成员 → 高亮可点 (status=active)
    @Test
    void apM5_readComment_mentionedUserStillMember_statusActive() throws Exception {
        String createBody = """
            {
                "body": "Hello @Editor!",
                "mentions": [
                    {
                        "user_id": "%s",
                        "display_name_snapshot": "Editor User"
                    }
                ]
            }
            """.formatted(EDITOR_USER_ID);

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String commentId = mapper.readTree(result).get("id").asText();

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + commentId)
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentions[0].user_id").value(EDITOR_USER_ID.toString()))
                .andExpect(jsonPath("$.mentions[0].status").value("active"));
    }

    // AP-M6: 被 @ 人后来退出 → 快照+失效；不可点；评论仍在 (status=left)
    @Test
    void apM6_readComment_mentionedUserLeft_statusLeft() throws Exception {
        UUID tempUserId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        familyStore.addMemberWithRole(FAMILY_ID, tempUserId, Role.EDITOR);

        String createBody = """
            {
                "body": "Hello @TempUser!",
                "mentions": [
                    {
                        "user_id": "%s",
                        "display_name_snapshot": "Temp User"
                    }
                ]
            }
            """.formatted(tempUserId);

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String commentId = mapper.readTree(result).get("id").asText();

        familyStore.removeMember(FAMILY_ID, tempUserId);

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + commentId)
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Hello @TempUser!"))
                .andExpect(jsonPath("$.mentions[0].user_id").value(tempUserId.toString()))
                .andExpect(jsonPath("$.mentions[0].display_name_snapshot").value("Temp User"))
                .andExpect(jsonPath("$.mentions[0].status").value("left"));
    }

    // AP-M8: 作者改己评增删 @（正确 updated_at）→ 成功；列表与 body 一致
    @Test
    void apM8_updateComment_addMention_success() throws Exception {
        String createBody = """
            {
                "body": "Original comment without mention"
            }
            """;

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mentions").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String commentId = mapper.readTree(result).get("id").asText();
        String updatedAt = mapper.readTree(result).get("updated_at").asText();

        String updateBody = """
            {
                "body": "Updated comment with @Admin mention",
                "mentions": [
                    {
                        "user_id": "%s",
                        "display_name_snapshot": "Admin User"
                    }
                ],
                "updated_at": "%s"
            }
            """.formatted(ADMIN_USER_ID, updatedAt);

        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + commentId)
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Updated comment with @Admin mention"))
                .andExpect(jsonPath("$.mentions", hasSize(1)))
                .andExpect(jsonPath("$.mentions[0].user_id").value(ADMIN_USER_ID.toString()));
    }

    @Test
    void apM8_updateComment_removeMention_success() throws Exception {
        String createBody = """
            {
                "body": "Comment with @Editor mention",
                "mentions": [
                    {
                        "user_id": "%s",
                        "display_name_snapshot": "Editor User"
                    }
                ]
            }
            """.formatted(EDITOR_USER_ID);

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mentions", hasSize(1)))
                .andReturn().getResponse().getContentAsString();

        String commentId = mapper.readTree(result).get("id").asText();
        String updatedAt = mapper.readTree(result).get("updated_at").asText();

        String updateBody = """
            {
                "body": "Updated comment without mention",
                "mentions": [],
                "updated_at": "%s"
            }
            """.formatted(updatedAt);

        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + commentId)
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Updated comment without mention"))
                .andExpect(jsonPath("$.mentions").doesNotExist());
    }

    @Test
    void apM8_updateComment_withInvalidMention_returns400() throws Exception {
        String createBody = """
            {
                "body": "Original comment"
            }
            """;

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String commentId = mapper.readTree(result).get("id").asText();
        String updatedAt = mapper.readTree(result).get("updated_at").asText();

        String updateBody = """
            {
                "body": "Updated with invalid mention",
                "mentions": [
                    {
                        "user_id": "%s",
                        "display_name_snapshot": "Non Member"
                    }
                ],
                "updated_at": "%s"
            }
            """.formatted(NON_MEMBER_USER_ID, updatedAt);

        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + commentId)
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("mentioned user is not a current family member"));
    }

    // List comments returns mentions
    @Test
    void listComments_includesMentions() throws Exception {
        String body1 = """
            {
                "body": "Hello @Editor!",
                "mentions": [
                    {
                        "user_id": "%s",
                        "display_name_snapshot": "Editor User"
                    }
                ]
            }
            """.formatted(EDITOR_USER_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body1))
                .andExpect(status().isCreated());

        String body2 = """
            {
                "body": "No mention here"
            }
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments", hasSize(2)))
                .andExpect(jsonPath("$.comments[0].mentions", hasSize(1)))
                .andExpect(jsonPath("$.comments[0].mentions[0].user_id").value(EDITOR_USER_ID.toString()))
                .andExpect(jsonPath("$.comments[1].mentions").doesNotExist());
    }

    // Mention requires display_name_snapshot
    @Test
    void createComment_mentionMissingDisplayName_returns400() throws Exception {
        String body = """
            {
                "body": "Hello @Editor!",
                "mentions": [
                    {
                        "user_id": "%s"
                    }
                ]
            }
            """.formatted(EDITOR_USER_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("mention display_name_snapshot is required"));
    }

    // Mention requires user_id
    @Test
    void createComment_mentionMissingUserId_returns400() throws Exception {
        String body = """
            {
                "body": "Hello @Editor!",
                "mentions": [
                    {
                        "display_name_snapshot": "Editor User"
                    }
                ]
            }
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("mention user_id is required"));
    }

    // Multiple mentions
    @Test
    void createComment_multipleMentions_success() throws Exception {
        String body = """
            {
                "body": "Hello @Admin and @Viewer!",
                "mentions": [
                    {
                        "user_id": "%s",
                        "display_name_snapshot": "Admin User"
                    },
                    {
                        "user_id": "%s",
                        "display_name_snapshot": "Viewer User"
                    }
                ]
            }
            """.formatted(ADMIN_USER_ID, VIEWER_USER_ID);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mentions", hasSize(2)));
    }

    // ====================
    // END MENTIONS TESTS
    // ====================

    // MAJOR-1 regression: viewer read-only even for own comments
    // Scenario: user creates comment as editor, then accesses as viewer (demoted/different token)
    // Expected: PUT own → 403, DELETE own → 403
    @Test
    void regression_viewerCannotEditOwnComment_returns403() throws Exception {
        // Create a new user who will be both editor and viewer in different contexts
        UUID dualRoleUserId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        
        // Add as editor first
        familyStore.addMemberWithRole(FAMILY_ID, dualRoleUserId, Role.EDITOR);

        // Create comment as editor
        String createBody = """
            {
                "body": "Comment created as editor"
            }
            """;

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(dualRoleUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String commentId = mapper.readTree(result).get("id").asText();
        String updatedAt = mapper.readTree(result).get("updated_at").asText();

        // Demote to viewer
        familyStore.updateMemberRole(FAMILY_ID, dualRoleUserId, Role.VIEWER);

        // Attempt to edit own comment as viewer → 403
        String updateBody = """
            {
                "body": "Trying to edit as viewer",
                "updated_at": "%s"
            }
            """.formatted(updatedAt);

        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + commentId)
                        .header(AUTH_HEADER, bearerToken(dualRoleUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("write access required"));
    }

    @Test
    void regression_viewerCannotDeleteOwnComment_returns403() throws Exception {
        // Create a new user who will be both editor and viewer in different contexts
        UUID dualRoleUserId = UUID.fromString("99999999-9999-9999-9999-999999999998");
        
        // Add as editor first
        familyStore.addMemberWithRole(FAMILY_ID, dualRoleUserId, Role.EDITOR);

        // Create comment as editor
        String createBody = """
            {
                "body": "Comment created as editor for delete test"
            }
            """;

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(dualRoleUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String commentId = mapper.readTree(result).get("id").asText();

        // Demote to viewer
        familyStore.updateMemberRole(FAMILY_ID, dualRoleUserId, Role.VIEWER);

        // Attempt to delete own comment as viewer → 403
        mockMvc.perform(delete("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + commentId)
                        .header(AUTH_HEADER, bearerToken(dualRoleUserId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("write access required"));

        // Verify comment still exists
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + commentId)
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Comment created as editor for delete test"));
    }
}
