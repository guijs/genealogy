package com.genealogy.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genealogy.domain.family.Role;
import com.genealogy.domain.person.Person;
import com.genealogy.domain.projection.Gender;
import com.genealogy.domain.projection.ProjectionPerson;
import com.genealogy.domain.story.Story;
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

class PersonRefControllerTest extends BaseIntegrationTest {

    private static final String AUTH_HEADER = "Authorization";

    @Autowired
    private FamilyStore familyStore;

    @Autowired
    private PersonStore personStore;

    @Autowired
    private ProjectionStore projectionStore;

    @Autowired
    private StoryStore storyStore;

    private static final UUID FAMILY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_FAMILY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ADMIN_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID EDITOR_USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID VIEWER_USER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID PERSON_ID_1 = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID PERSON_ID_2 = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID PERSON_ID_HIDDEN = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID PERSON_ID_DECEASED = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID PERSON_ID_OTHER_FAMILY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String FAMILY_NAME = "Test Family";

    private UUID storyId;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        cleanAllData();

        familyStore.createFamily(FAMILY_ID, FAMILY_NAME);
        familyStore.addMemberWithRole(FAMILY_ID, ADMIN_USER_ID, Role.ADMIN);
        familyStore.addMemberWithRole(FAMILY_ID, EDITOR_USER_ID, Role.EDITOR);
        familyStore.addMemberWithRole(FAMILY_ID, VIEWER_USER_ID, Role.VIEWER);

        familyStore.createFamily(OTHER_FAMILY_ID, "Other Family");

        personStore.addPerson(new Person(PERSON_ID_1, FAMILY_ID, "张", "三"));
        projectionStore.createPerson(new ProjectionPerson(
                PERSON_ID_1, FAMILY_ID, "张三", Gender.MALE, 1980, null, false));

        personStore.addPerson(new Person(PERSON_ID_2, FAMILY_ID, "李", "四"));
        projectionStore.createPerson(new ProjectionPerson(
                PERSON_ID_2, FAMILY_ID, "李四", Gender.FEMALE, 1985, null, false));

        personStore.addPerson(new Person(PERSON_ID_HIDDEN, FAMILY_ID, "隐藏", "人物"));
        projectionStore.createPerson(new ProjectionPerson(
                PERSON_ID_HIDDEN, FAMILY_ID, "隐藏人物", Gender.MALE, 1970, null, true));

        personStore.addPerson(new Person(PERSON_ID_DECEASED, FAMILY_ID, "已故", "人物"));
        projectionStore.createPerson(new ProjectionPerson(
                PERSON_ID_DECEASED, FAMILY_ID, "已故人物", Gender.MALE, 1920, 2000, false));

        personStore.addPerson(new Person(PERSON_ID_OTHER_FAMILY, OTHER_FAMILY_ID, "其他", "家族"));
        projectionStore.createPerson(new ProjectionPerson(
                PERSON_ID_OTHER_FAMILY, OTHER_FAMILY_ID, "其他家族", Gender.MALE, 1990, null, false));

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
    }

    // ====================
    // COMMENT PERSON_REFS TESTS
    // ====================

    // AP-PR1: editor 在评论中插入本家族 Person → 成功；person_refs 含该 person_id
    @Test
    void apPR1_createComment_withValidPersonRef_returns201() throws Exception {
        String body = """
            {
                "body": "这是一个引用了#张三的评论",
                "person_refs": [
                    {
                        "person_id": "%s",
                        "display_name_snapshot": "张三"
                    }
                ]
            }
            """.formatted(PERSON_ID_1);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("这是一个引用了#张三的评论"))
                .andExpect(jsonPath("$.person_refs", hasSize(1)))
                .andExpect(jsonPath("$.person_refs[0].person_id").value(PERSON_ID_1.toString()))
                .andExpect(jsonPath("$.person_refs[0].display_name_snapshot").value("张三"))
                .andExpect(jsonPath("$.person_refs[0].status").value("active"));
    }

    // AP-PR4: 提交他家族 / 硬删 person_id → 400
    @Test
    void apPR4_createComment_withOtherFamilyPerson_returns400() throws Exception {
        String body = """
            {
                "body": "引用其他家族的人",
                "person_refs": [
                    {
                        "person_id": "%s",
                        "display_name_snapshot": "其他家族"
                    }
                ]
            }
            """.formatted(PERSON_ID_OTHER_FAMILY);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("referenced person is not in this family or has been deleted"));
    }

    @Test
    void apPR4_createComment_withNonExistentPerson_returns400() throws Exception {
        UUID nonExistentPersonId = UUID.randomUUID();
        String body = """
            {
                "body": "引用不存在的人",
                "person_refs": [
                    {
                        "person_id": "%s",
                        "display_name_snapshot": "不存在"
                    }
                ]
            }
            """.formatted(nonExistentPersonId);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("referenced person is not in this family or has been deleted"));
    }

    // AP-PR5: 引用已故且对读者可见 → 可展示
    @Test
    void apPR5_createComment_withDeceasedPerson_returns201() throws Exception {
        String body = """
            {
                "body": "引用已故人物",
                "person_refs": [
                    {
                        "person_id": "%s",
                        "display_name_snapshot": "已故人物"
                    }
                ]
            }
            """.formatted(PERSON_ID_DECEASED);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.person_refs[0].person_id").value(PERSON_ID_DECEASED.toString()))
                .andExpect(jsonPath("$.person_refs[0].status").value("active"));
    }

    // AP-PR6: 被引人物对 viewer 读者 Hidden → status=hidden, clickable=false
    // 对 admin/editor 读者 Hidden → status=active, clickable=true
    @Test
    void apPR6_readComment_withHiddenPerson_statusHiddenForViewer() throws Exception {
        String createBody = """
            {
                "body": "引用隐藏人物",
                "person_refs": [
                    {
                        "person_id": "%s",
                        "display_name_snapshot": "隐藏人物"
                    }
                ]
            }
            """.formatted(PERSON_ID_HIDDEN);

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String commentId = mapper.readTree(result).get("id").asText();

        // viewer 读取 - Hidden Person 对 viewer 不可见，status=hidden, clickable=false
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + commentId)
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.person_refs[0].person_id").value(PERSON_ID_HIDDEN.toString()))
                .andExpect(jsonPath("$.person_refs[0].display_name_snapshot").value("隐藏人物"))
                .andExpect(jsonPath("$.person_refs[0].status").value("hidden"))
                .andExpect(jsonPath("$.person_refs[0].clickable").value(false));

        // admin 读取 - Hidden Person 对 admin 可见，status=active, clickable=true
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + commentId)
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.person_refs[0].person_id").value(PERSON_ID_HIDDEN.toString()))
                .andExpect(jsonPath("$.person_refs[0].display_name_snapshot").value("隐藏人物"))
                .andExpect(jsonPath("$.person_refs[0].status").value("active"))
                .andExpect(jsonPath("$.person_refs[0].clickable").value(true));
    }

    // AP-PR8: 手敲 #张三 无选人 → 无 person_refs 条目
    @Test
    void apPR8_createComment_plainTextHashRef_noStructuredPersonRef() throws Exception {
        String body = """
            {
                "body": "提到了#张三，但没有结构化引用"
            }
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("提到了#张三，但没有结构化引用"))
                .andExpect(jsonPath("$.person_refs").doesNotExist());
    }

    // AP-PR10: viewer 只读；不可在不可写场景插入
    @Test
    void apPR10_createComment_viewer_returns403() throws Exception {
        String body = """
            {
                "body": "Viewer trying to add person_ref",
                "person_refs": [
                    {
                        "person_id": "%s",
                        "display_name_snapshot": "张三"
                    }
                ]
            }
            """.formatted(PERSON_ID_1);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // person_ref requires display_name_snapshot
    @Test
    void createComment_personRefMissingDisplayName_returns400() throws Exception {
        String body = """
            {
                "body": "Missing display_name_snapshot",
                "person_refs": [
                    {
                        "person_id": "%s"
                    }
                ]
            }
            """.formatted(PERSON_ID_1);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("person_ref display_name_snapshot is required"));
    }

    // person_ref requires person_id
    @Test
    void createComment_personRefMissingPersonId_returns400() throws Exception {
        String body = """
            {
                "body": "Missing person_id",
                "person_refs": [
                    {
                        "display_name_snapshot": "张三"
                    }
                ]
            }
            """;

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("person_ref person_id is required"));
    }

    // Multiple person_refs
    @Test
    void createComment_multiplePersonRefs_success() throws Exception {
        String body = """
            {
                "body": "引用多人",
                "person_refs": [
                    {
                        "person_id": "%s",
                        "display_name_snapshot": "张三"
                    },
                    {
                        "person_id": "%s",
                        "display_name_snapshot": "李四"
                    }
                ]
            }
            """.formatted(PERSON_ID_1, PERSON_ID_2);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.person_refs", hasSize(2)));
    }

    // Update semantics: omit person_refs → do not change
    @Test
    void updateComment_omitPersonRefs_preservesExisting() throws Exception {
        String createBody = """
            {
                "body": "Original with person_ref",
                "person_refs": [
                    {
                        "person_id": "%s",
                        "display_name_snapshot": "张三"
                    }
                ]
            }
            """.formatted(PERSON_ID_1);

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.person_refs", hasSize(1)))
                .andReturn().getResponse().getContentAsString();

        String commentId = mapper.readTree(result).get("id").asText();
        String updatedAt = mapper.readTree(result).get("updated_at").asText();

        String updateBody = """
            {
                "body": "Updated body without touching person_refs",
                "updated_at": "%s"
            }
            """.formatted(updatedAt);

        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + commentId)
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Updated body without touching person_refs"))
                .andExpect(jsonPath("$.person_refs", hasSize(1)))
                .andExpect(jsonPath("$.person_refs[0].person_id").value(PERSON_ID_1.toString()));
    }

    // Update semantics: explicit [] → clear all refs
    @Test
    void updateComment_emptyPersonRefs_clearsAll() throws Exception {
        String createBody = """
            {
                "body": "Original with person_ref",
                "person_refs": [
                    {
                        "person_id": "%s",
                        "display_name_snapshot": "张三"
                    }
                ]
            }
            """.formatted(PERSON_ID_1);

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.person_refs", hasSize(1)))
                .andReturn().getResponse().getContentAsString();

        String commentId = mapper.readTree(result).get("id").asText();
        String updatedAt = mapper.readTree(result).get("updated_at").asText();

        String updateBody = """
            {
                "body": "Updated body with cleared person_refs",
                "person_refs": [],
                "updated_at": "%s"
            }
            """.formatted(updatedAt);

        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + commentId)
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Updated body with cleared person_refs"))
                .andExpect(jsonPath("$.person_refs").doesNotExist());
    }

    // Update semantics: explicit non-empty → replace
    @Test
    void updateComment_explicitPersonRefs_replaces() throws Exception {
        String createBody = """
            {
                "body": "Original with person_ref",
                "person_refs": [
                    {
                        "person_id": "%s",
                        "display_name_snapshot": "张三"
                    }
                ]
            }
            """.formatted(PERSON_ID_1);

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.person_refs", hasSize(1)))
                .andReturn().getResponse().getContentAsString();

        String commentId = mapper.readTree(result).get("id").asText();
        String updatedAt = mapper.readTree(result).get("updated_at").asText();

        String updateBody = """
            {
                "body": "Updated body with replaced person_refs",
                "person_refs": [
                    {
                        "person_id": "%s",
                        "display_name_snapshot": "李四"
                    }
                ],
                "updated_at": "%s"
            }
            """.formatted(PERSON_ID_2, updatedAt);

        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + commentId)
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Updated body with replaced person_refs"))
                .andExpect(jsonPath("$.person_refs", hasSize(1)))
                .andExpect(jsonPath("$.person_refs[0].person_id").value(PERSON_ID_2.toString()));
    }

    // ====================
    // STORY PERSON_REFS TESTS
    // ====================

    // AP-PR2: editor 在故事中插入 Person → 成功；故事级 person_refs 正确
    @Test
    void apPR2_createStory_withValidPersonRef_returns201() throws Exception {
        String body = """
            {
                "title": "带人物引用的故事",
                "body": "这是一个引用了#张三的故事",
                "person_refs": [
                    {
                        "person_id": "%s",
                        "display_name_snapshot": "张三"
                    }
                ]
            }
            """.formatted(PERSON_ID_1);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("带人物引用的故事"))
                .andExpect(jsonPath("$.person_refs", hasSize(1)))
                .andExpect(jsonPath("$.person_refs[0].person_id").value(PERSON_ID_1.toString()))
                .andExpect(jsonPath("$.person_refs[0].display_name_snapshot").value("张三"))
                .andExpect(jsonPath("$.person_refs[0].status").value("active"));
    }

    // AP-PR4 for story: 提交他家族 person_id → 400
    @Test
    void apPR4_createStory_withOtherFamilyPerson_returns400() throws Exception {
        String body = """
            {
                "title": "引用其他家族",
                "body": "引用其他家族的人",
                "person_refs": [
                    {
                        "person_id": "%s",
                        "display_name_snapshot": "其他家族"
                    }
                ]
            }
            """.formatted(PERSON_ID_OTHER_FAMILY);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("referenced person is not in this family or has been deleted"));
    }

    // Story update semantics
    @Test
    void updateStory_omitPersonRefs_preservesExisting() throws Exception {
        String createBody = """
            {
                "title": "Original Story",
                "body": "Original body",
                "person_refs": [
                    {
                        "person_id": "%s",
                        "display_name_snapshot": "张三"
                    }
                ]
            }
            """.formatted(PERSON_ID_1);

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.person_refs", hasSize(1)))
                .andReturn().getResponse().getContentAsString();

        String newStoryId = mapper.readTree(result).get("id").asText();
        int version = mapper.readTree(result).get("version").asInt();

        String updateBody = """
            {
                "title": "Updated Story",
                "body": "Updated body without touching person_refs",
                "version": %d
            }
            """.formatted(version);

        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/stories/" + newStoryId)
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Story"))
                .andExpect(jsonPath("$.person_refs", hasSize(1)))
                .andExpect(jsonPath("$.person_refs[0].person_id").value(PERSON_ID_1.toString()));
    }

    @Test
    void updateStory_emptyPersonRefs_clearsAll() throws Exception {
        String createBody = """
            {
                "title": "Original Story",
                "body": "Original body",
                "person_refs": [
                    {
                        "person_id": "%s",
                        "display_name_snapshot": "张三"
                    }
                ]
            }
            """.formatted(PERSON_ID_1);

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.person_refs", hasSize(1)))
                .andReturn().getResponse().getContentAsString();

        String newStoryId = mapper.readTree(result).get("id").asText();
        int version = mapper.readTree(result).get("version").asInt();

        String updateBody = """
            {
                "title": "Updated Story",
                "body": "Updated body with cleared person_refs",
                "person_refs": [],
                "version": %d
            }
            """.formatted(version);

        mockMvc.perform(put("/api/v1/families/" + FAMILY_ID + "/stories/" + newStoryId)
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Story"))
                .andExpect(jsonPath("$.person_refs").doesNotExist());
    }

    // List comments includes person_refs
    @Test
    void listComments_includesPersonRefs() throws Exception {
        String body1 = """
            {
                "body": "评论1有引用",
                "person_refs": [
                    {
                        "person_id": "%s",
                        "display_name_snapshot": "张三"
                    }
                ]
            }
            """.formatted(PERSON_ID_1);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body1))
                .andExpect(status().isCreated());

        String body2 = """
            {
                "body": "评论2无引用"
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
                .andExpect(jsonPath("$.comments[0].person_refs", hasSize(1)))
                .andExpect(jsonPath("$.comments[0].person_refs[0].person_id").value(PERSON_ID_1.toString()))
                .andExpect(jsonPath("$.comments[1].person_refs").doesNotExist());
    }

    // Both mentions and person_refs can coexist
    @Test
    void createComment_withBothMentionsAndPersonRefs_success() throws Exception {
        String body = """
            {
                "body": "同时有@提及和#人物引用",
                "mentions": [
                    {
                        "user_id": "%s",
                        "display_name_snapshot": "Editor User"
                    }
                ],
                "person_refs": [
                    {
                        "person_id": "%s",
                        "display_name_snapshot": "张三"
                    }
                ]
            }
            """.formatted(EDITOR_USER_ID, PERSON_ID_1);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mentions", hasSize(1)))
                .andExpect(jsonPath("$.mentions[0].user_id").value(EDITOR_USER_ID.toString()))
                .andExpect(jsonPath("$.person_refs", hasSize(1)))
                .andExpect(jsonPath("$.person_refs[0].person_id").value(PERSON_ID_1.toString()));
    }

    // ====================
    // 补充契约 §1: clickable 字段测试
    // ====================

    @Test
    void readPersonRef_active_clickableTrue() throws Exception {
        String body = """
            {
                "body": "引用正常人物",
                "person_refs": [
                    {
                        "person_id": "%s",
                        "display_name_snapshot": "张三"
                    }
                ]
            }
            """.formatted(PERSON_ID_1);

        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.person_refs[0].status").value("active"))
                .andExpect(jsonPath("$.person_refs[0].clickable").value(true))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void readPersonRef_hidden_clickableFalseForViewer() throws Exception {
        String createBody = """
            {
                "body": "引用隐藏人物",
                "person_refs": [
                    {
                        "person_id": "%s",
                        "display_name_snapshot": "隐藏人物"
                    }
                ]
            }
            """.formatted(PERSON_ID_HIDDEN);

        // admin 创建，对 admin 来说 status=active, clickable=true
        String result = mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments")
                        .header(AUTH_HEADER, bearerToken(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.person_refs[0].status").value("active"))
                .andExpect(jsonPath("$.person_refs[0].clickable").value(true))
                .andReturn().getResponse().getContentAsString();

        String commentId = mapper.readTree(result).get("id").asText();

        // viewer 读取时 status=hidden, clickable=false
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/stories/" + storyId + "/comments/" + commentId)
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.person_refs[0].status").value("hidden"))
                .andExpect(jsonPath("$.person_refs[0].clickable").value(false));
    }

    @Test
    void storyPersonRef_active_clickableTrue() throws Exception {
        String body = """
            {
                "title": "测试故事",
                "body": "引用正常人物",
                "person_refs": [
                    {
                        "person_id": "%s",
                        "display_name_snapshot": "张三"
                    }
                ]
            }
            """.formatted(PERSON_ID_1);

        mockMvc.perform(post("/api/v1/families/" + FAMILY_ID + "/stories")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.person_refs[0].status").value("active"))
                .andExpect(jsonPath("$.person_refs[0].clickable").value(true));
    }

    // ====================
    // 补充契约 §5: 候选 API 测试
    // ====================

    @Test
    void listPersonRefCandidates_editorSeesAllIncludingHidden() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/person-ref-candidates")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates", hasSize(4)))
                .andExpect(jsonPath("$.candidates[*].person_id", hasItem(PERSON_ID_1.toString())))
                .andExpect(jsonPath("$.candidates[*].person_id", hasItem(PERSON_ID_2.toString())))
                .andExpect(jsonPath("$.candidates[*].person_id", hasItem(PERSON_ID_HIDDEN.toString())))
                .andExpect(jsonPath("$.candidates[*].person_id", hasItem(PERSON_ID_DECEASED.toString())));
    }

    @Test
    void listPersonRefCandidates_viewerDoesNotSeeHidden() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/person-ref-candidates")
                        .header(AUTH_HEADER, bearerToken(VIEWER_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates", hasSize(3)))
                .andExpect(jsonPath("$.candidates[*].person_id", hasItem(PERSON_ID_1.toString())))
                .andExpect(jsonPath("$.candidates[*].person_id", hasItem(PERSON_ID_2.toString())))
                .andExpect(jsonPath("$.candidates[*].person_id", hasItem(PERSON_ID_DECEASED.toString())))
                .andExpect(jsonPath("$.candidates[*].person_id", not(hasItem(PERSON_ID_HIDDEN.toString()))));
    }

    @Test
    void listPersonRefCandidates_includesDeceasedInfo() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/person-ref-candidates")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[?(@.person_id=='%s')].deceased", PERSON_ID_DECEASED.toString()).value(true))
                .andExpect(jsonPath("$.candidates[?(@.person_id=='%s')].deceased", PERSON_ID_1.toString()).value(false));
    }

    @Test
    void listPersonRefCandidates_excludesOtherFamily() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/person-ref-candidates")
                        .header(AUTH_HEADER, bearerToken(EDITOR_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[*].person_id", not(hasItem(PERSON_ID_OTHER_FAMILY.toString()))));
    }
}
