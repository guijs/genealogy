package com.genealogy.store;

import com.genealogy.domain.projection.ProjectionPerson;
import com.genealogy.domain.story.CommentMention;
import com.genealogy.domain.story.CommentPersonRef;
import com.genealogy.domain.story.PersonRefStatus;
import com.genealogy.domain.story.StoryComment;
import com.genealogy.mapper.CommentMentionMapper;
import com.genealogy.mapper.CommentPersonRefMapper;
import com.genealogy.mapper.FamilyMemberMapper;
import com.genealogy.mapper.PersonMapper;
import com.genealogy.mapper.StoryCommentMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class StoryCommentStore {
    private final StoryCommentMapper commentMapper;
    private final CommentMentionMapper mentionMapper;
    private final CommentPersonRefMapper personRefMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final PersonMapper personMapper;

    public StoryCommentStore(StoryCommentMapper commentMapper,
                             CommentMentionMapper mentionMapper,
                             CommentPersonRefMapper personRefMapper,
                             FamilyMemberMapper familyMemberMapper,
                             PersonMapper personMapper) {
        this.commentMapper = commentMapper;
        this.mentionMapper = mentionMapper;
        this.personRefMapper = personRefMapper;
        this.familyMemberMapper = familyMemberMapper;
        this.personMapper = personMapper;
    }

    public sealed interface WriteResult {
        record Success() implements WriteResult {}
        record NotFound() implements WriteResult {}
        record VersionConflict(StoryComment currentComment) implements WriteResult {}
    }

    public void createComment(StoryComment comment, List<MentionInput> mentions, List<PersonRefInput> personRefs) {
        commentMapper.insertComment(
                comment.getId(),
                comment.getStoryId(),
                comment.getAuthorUserId(),
                comment.getBody()
        );
        if (mentions != null) {
            for (MentionInput mention : mentions) {
                mentionMapper.insert(
                        UUID.randomUUID(),
                        comment.getId(),
                        mention.userId(),
                        mention.displayNameSnapshot()
                );
            }
        }
        if (personRefs != null) {
            for (PersonRefInput ref : personRefs) {
                personRefMapper.insert(
                        UUID.randomUUID(),
                        comment.getId(),
                        ref.personId(),
                        ref.displayNameSnapshot()
                );
            }
        }
    }

    public record MentionInput(UUID userId, String displayNameSnapshot) {}
    public record PersonRefInput(UUID personId, String displayNameSnapshot) {}

    public Optional<StoryComment> getComment(UUID id, UUID familyId) {
        StoryCommentMapper.CommentRow row = commentMapper.findById(id);
        if (row == null) {
            return Optional.empty();
        }
        List<CommentMention> mentions = fetchMentionsForComment(id, familyId);
        List<CommentPersonRef> personRefs = fetchPersonRefsForComment(id);
        return Optional.of(toComment(row, mentions, personRefs));
    }

    public Optional<StoryComment> getCommentByIdAndStoryId(UUID id, UUID storyId, UUID familyId) {
        StoryCommentMapper.CommentRow row = commentMapper.findByIdAndStoryId(id, storyId);
        if (row == null) {
            return Optional.empty();
        }
        List<CommentMention> mentions = fetchMentionsForComment(id, familyId);
        List<CommentPersonRef> personRefs = fetchPersonRefsForComment(id);
        return Optional.of(toComment(row, mentions, personRefs));
    }

    public List<StoryComment> listByStoryId(UUID storyId, UUID familyId) {
        List<StoryCommentMapper.CommentRow> rows = commentMapper.findByStoryId(storyId);
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> commentIds = rows.stream().map(StoryCommentMapper.CommentRow::id).collect(Collectors.toList());
        Map<UUID, List<CommentMention>> mentionsByComment = fetchMentionsForComments(commentIds, familyId);
        Map<UUID, List<CommentPersonRef>> personRefsByComment = fetchPersonRefsForComments(commentIds);

        List<StoryComment> comments = new ArrayList<>();
        for (StoryCommentMapper.CommentRow row : rows) {
            List<CommentMention> mentions = mentionsByComment.getOrDefault(row.id(), Collections.emptyList());
            List<CommentPersonRef> personRefs = personRefsByComment.getOrDefault(row.id(), Collections.emptyList());
            comments.add(toComment(row, mentions, personRefs));
        }
        return comments;
    }

    public WriteResult updateComment(UUID id, String body, Instant expectedUpdatedAt,
                                     List<MentionInput> mentions, List<PersonRefInput> personRefs,
                                     boolean personRefsProvided, UUID familyId) {
        int rowsAffected = commentMapper.updateComment(id, body, expectedUpdatedAt);

        if (rowsAffected == 0) {
            Optional<StoryComment> current = getComment(id, familyId);
            if (current.isEmpty()) {
                return new WriteResult.NotFound();
            }
            return new WriteResult.VersionConflict(current.get());
        }

        mentionMapper.deleteByCommentId(id);
        if (mentions != null) {
            for (MentionInput mention : mentions) {
                mentionMapper.insert(
                        UUID.randomUUID(),
                        id,
                        mention.userId(),
                        mention.displayNameSnapshot()
                );
            }
        }

        if (personRefsProvided) {
            personRefMapper.deleteByCommentId(id);
            if (personRefs != null) {
                for (PersonRefInput ref : personRefs) {
                    personRefMapper.insert(
                            UUID.randomUUID(),
                            id,
                            ref.personId(),
                            ref.displayNameSnapshot()
                    );
                }
            }
        }

        return new WriteResult.Success();
    }

    private List<CommentMention> fetchMentionsForComment(UUID commentId, UUID familyId) {
        List<CommentMentionMapper.MentionRow> rows = mentionMapper.findByCommentId(commentId);
        return toMentionsWithMembershipStatus(rows, familyId);
    }

    private Map<UUID, List<CommentMention>> fetchMentionsForComments(List<UUID> commentIds, UUID familyId) {
        if (commentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<CommentMentionMapper.MentionRow> rows = mentionMapper.findByCommentIds(commentIds);
        List<CommentMention> mentions = toMentionsWithMembershipStatus(rows, familyId);
        return mentions.stream().collect(Collectors.groupingBy(CommentMention::getCommentId));
    }

    private List<CommentMention> toMentionsWithMembershipStatus(List<CommentMentionMapper.MentionRow> rows, UUID familyId) {
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        Set<UUID> userIds = rows.stream()
                .map(CommentMentionMapper.MentionRow::userId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<UUID> currentMemberIds = new HashSet<>();
        for (UUID userId : userIds) {
            if (familyMemberMapper.existsByFamilyAndUser(familyId, userId)) {
                currentMemberIds.add(userId);
            }
        }

        return rows.stream()
                .map(row -> new CommentMention(
                        row.id(),
                        row.commentId(),
                        row.userId(),
                        row.displayNameSnapshot(),
                        row.createdAt(),
                        row.userId() != null && currentMemberIds.contains(row.userId())
                ))
                .collect(Collectors.toList());
    }

    private List<CommentPersonRef> fetchPersonRefsForComment(UUID commentId) {
        List<CommentPersonRefMapper.PersonRefRow> rows = personRefMapper.findByCommentId(commentId);
        return toPersonRefsWithStatus(rows);
    }

    private Map<UUID, List<CommentPersonRef>> fetchPersonRefsForComments(List<UUID> commentIds) {
        if (commentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<CommentPersonRefMapper.PersonRefRow> rows = personRefMapper.findByCommentIds(commentIds);
        List<CommentPersonRef> refs = toPersonRefsWithStatus(rows);
        return refs.stream().collect(Collectors.groupingBy(CommentPersonRef::getCommentId));
    }

    private List<CommentPersonRef> toPersonRefsWithStatus(List<CommentPersonRefMapper.PersonRefRow> rows) {
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        Set<UUID> personIds = rows.stream()
                .map(CommentPersonRefMapper.PersonRefRow::personId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, ProjectionPerson> personMap = new HashMap<>();
        for (UUID personId : personIds) {
            ProjectionPerson person = personMapper.findProjectionById(personId);
            if (person != null) {
                personMap.put(personId, person);
            }
        }

        return rows.stream()
                .map(row -> {
                    PersonRefStatus status;
                    if (row.personId() == null) {
                        status = PersonRefStatus.DELETED;
                    } else {
                        ProjectionPerson person = personMap.get(row.personId());
                        if (person == null) {
                            status = PersonRefStatus.DELETED;
                        } else if (person.isHidden()) {
                            status = PersonRefStatus.HIDDEN;
                        } else {
                            status = PersonRefStatus.ACTIVE;
                        }
                    }
                    return new CommentPersonRef(
                            row.id(),
                            row.commentId(),
                            row.personId(),
                            row.displayNameSnapshot(),
                            row.createdAt(),
                            status
                    );
                })
                .collect(Collectors.toList());
    }

    public WriteResult deleteComment(UUID id) {
        int rowsAffected = commentMapper.deleteById(id);

        if (rowsAffected == 0) {
            return new WriteResult.NotFound();
        }
        return new WriteResult.Success();
    }

    public void clear() {
        personRefMapper.deleteAll();
        mentionMapper.deleteAll();
        commentMapper.deleteAll();
    }

    private StoryComment toComment(StoryCommentMapper.CommentRow row, List<CommentMention> mentions,
                                   List<CommentPersonRef> personRefs) {
        return new StoryComment(
                row.id(),
                row.storyId(),
                row.authorUserId(),
                row.body(),
                row.createdAt(),
                row.updatedAt(),
                mentions,
                personRefs
        );
    }
}
