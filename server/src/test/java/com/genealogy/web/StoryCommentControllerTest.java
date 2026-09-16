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

    // AP-C1: All roles can create comment (viewer post)
    @Test
    void createComment_viewer_returns201() throws Exception {
        String body = """
            {
                "body": "Viewer comment on story"
            }
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.story_id").value(storyId.toString()))
                .andExpect(jsonPath("$.author_user_id").value(VIEWER_USER_ID.toString()))
                .andExpect(jsonPath("$.body").value("Viewer comment on story"))
                .andExpect(jsonPath("$.created_at").isNotEmpty())
                .andExpect(jsonPath("$.parent_comment_id").doesNotExist());
    }

    @Test
    void createComment_admin_returns201() throws Exception {
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
                .andExpect(jsonPath("$.author_user_id").value(ADMIN_USER_ID.toString()));
    }

    @Test
    void createComment_editor_returns201() throws Exception {
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
                .andExpect(jsonPath("$.author_user_id").value(EDITOR_USER_ID.toString()));
    }

    // AP-C2: non-member cannot create comment
    @Test
    void createComment_nonMember_returns404() throws Exception {
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

    // AP-C3: one-level reply ok (reply to root comment)
    @Test
    void createComment_replyToRoot_returns201() throws Exception {
        String rootBody = """
            {
                "body": "Root comment"
            }
            """;

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rootBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String rootCommentId = mapper.readTree(result).get("id").asText();

        String replyBody = """
            {
                "body": "Reply to root comment",
                "parent_comment_id": "%s"
            }
            """.formatted(rootCommentId);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replyBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parent_comment_id").value(rootCommentId))
                .andExpect(jsonPath("$.body").value("Reply to root comment"));
    }

    // AP-C4: reply-to-reply rejected
    @Test
    void createComment_replyToReply_returns400() throws Exception {
        String rootBody = """
            {
                "body": "Root comment"
            }
            """;

        String rootResult = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rootBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String rootCommentId = mapper.readTree(rootResult).get("id").asText();

        String replyBody = """
            {
                "body": "Reply to root",
                "parent_comment_id": "%s"
            }
            """.formatted(rootCommentId);

        String replyResult = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replyBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String replyCommentId = mapper.readTree(replyResult).get("id").asText();

        String replyToReplyBody = """
            {
                "body": "Reply to reply (should fail)",
                "parent_comment_id": "%s"
            }
            """.formatted(replyCommentId);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replyToReplyBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("reply-to-reply not allowed; parent must be a root comment"));
    }

    // AP-C5: author can delete own comment
    @Test
    void deleteComment_author_returns204() throws Exception {
        String createBody = """
            {
                "body": "Comment to delete"
            }
            """;

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String commentId = mapper.readTree(result).get("id").asText();

        mockMvc.perform(delete("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + commentId)
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + commentId)
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isNotFound());
    }

    // AP-C6: admin can delete other's comment
    @Test
    void deleteComment_admin_deleteOther_returns204() throws Exception {
        String createBody = """
            {
                "body": "Viewer's comment"
            }
            """;

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String commentId = mapper.readTree(result).get("id").asText();

        mockMvc.perform(delete("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + commentId)
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID)))
                .andExpect(status().isNoContent());
    }

    // AP-C6: editor can delete other's comment
    @Test
    void deleteComment_editor_deleteOther_returns204() throws Exception {
        String createBody = """
            {
                "body": "Viewer's comment"
            }
            """;

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String commentId = mapper.readTree(result).get("id").asText();

        mockMvc.perform(delete("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + commentId)
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID)))
                .andExpect(status().isNoContent());
    }

    // AP-C7: viewer cannot delete other's comment
    @Test
    void deleteComment_viewer_deleteOther_returns403() throws Exception {
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
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("only author or admin/editor can delete comments"));
    }

    // AP-C8: Hidden story - viewer cannot see comments
    @Test
    void listComments_hiddenStory_viewer_returns404() throws Exception {
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
    void listComments_hiddenStory_admin_returns200() throws Exception {
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

    // AP-C9: body > 1000 characters - rejected
    @Test
    void createComment_bodyTooLong_returns400() throws Exception {
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
    void createComment_bodyAtMaxLength_returns201() throws Exception {
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

    // AP-C10: no edit endpoint
    @Test
    void updateComment_returns405() throws Exception {
        String createBody = """
            {
                "body": "Original comment"
            }
            """;

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
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
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isMethodNotAllowed());
    }

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

    // Parent comment not found
    @Test
    void createComment_parentNotFound_returns400() throws Exception {
        UUID unknownParentId = UUID.randomUUID();
        String body = """
            {
                "body": "Reply to unknown parent",
                "parent_comment_id": "%s"
            }
            """.formatted(unknownParentId);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("parent comment not found"));
    }

    // List comments with parent_id (flat representation)
    @Test
    void listComments_returnsWithParentId() throws Exception {
        String rootBody = """
            {
                "body": "Root comment"
            }
            """;

        String rootResult = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rootBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String rootCommentId = mapper.readTree(rootResult).get("id").asText();

        String replyBody = """
            {
                "body": "Reply comment",
                "parent_comment_id": "%s"
            }
            """.formatted(rootCommentId);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replyBody))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments", hasSize(2)))
                .andExpect(jsonPath("$.comments[0].parent_comment_id").doesNotExist())
                .andExpect(jsonPath("$.comments[1].parent_comment_id").value(rootCommentId));
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
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID))
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

    // Delete parent comment cascades to replies
    @Test
    void deleteComment_parentCascadesToReplies() throws Exception {
        String rootBody = """
            {
                "body": "Root comment"
            }
            """;

        String rootResult = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rootBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String rootCommentId = mapper.readTree(rootResult).get("id").asText();

        String replyBody = """
            {
                "body": "Reply comment",
                "parent_comment_id": "%s"
            }
            """.formatted(rootCommentId);

        String replyResult = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replyBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String replyCommentId = mapper.readTree(replyResult).get("id").asText();

        mockMvc.perform(delete("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + rootCommentId)
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID)))
                .andExpect(status().isNoContent());

        StoryCommentMapper.CommentRow deletedReply = commentMapper.findById(UUID.fromString(replyCommentId));
        assert deletedReply == null : "Reply should be cascade deleted with parent";
    }
}
