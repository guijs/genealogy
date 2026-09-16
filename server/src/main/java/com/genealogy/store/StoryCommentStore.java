package com.genealogy.store;

import com.genealogy.domain.story.CommentMention;
import com.genealogy.domain.story.StoryComment;
import com.genealogy.mapper.CommentMentionMapper;
import com.genealogy.mapper.FamilyMemberMapper;
import com.genealogy.mapper.StoryCommentMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class StoryCommentStore {
    private final StoryCommentMapper commentMapper;
    private final CommentMentionMapper mentionMapper;
    private final FamilyMemberMapper familyMemberMapper;

    public StoryCommentStore(StoryCommentMapper commentMapper,
                             CommentMentionMapper mentionMapper,
                             FamilyMemberMapper familyMemberMapper) {
        this.commentMapper = commentMapper;
        this.mentionMapper = mentionMapper;
        this.familyMemberMapper = familyMemberMapper;
    }

    public sealed interface WriteResult {
        record Success() implements WriteResult {}
        record NotFound() implements WriteResult {}
        record VersionConflict(StoryComment currentComment) implements WriteResult {}
    }

    public void createComment(StoryComment comment, List<MentionInput> mentions) {
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
    }

    public record MentionInput(UUID userId, String displayNameSnapshot) {}

    public Optional<StoryComment> getComment(UUID id, UUID familyId) {
        StoryCommentMapper.CommentRow row = commentMapper.findById(id);
        if (row == null) {
            return Optional.empty();
        }
        List<CommentMention> mentions = fetchMentionsForComment(id, familyId);
        return Optional.of(toComment(row, mentions));
    }

    public Optional<StoryComment> getCommentByIdAndStoryId(UUID id, UUID storyId, UUID familyId) {
        StoryCommentMapper.CommentRow row = commentMapper.findByIdAndStoryId(id, storyId);
        if (row == null) {
            return Optional.empty();
        }
        List<CommentMention> mentions = fetchMentionsForComment(id, familyId);
        return Optional.of(toComment(row, mentions));
    }

    public List<StoryComment> listByStoryId(UUID storyId, UUID familyId) {
        List<StoryCommentMapper.CommentRow> rows = commentMapper.findByStoryId(storyId);
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> commentIds = rows.stream().map(StoryCommentMapper.CommentRow::id).collect(Collectors.toList());
        Map<UUID, List<CommentMention>> mentionsByComment = fetchMentionsForComments(commentIds, familyId);

        List<StoryComment> comments = new ArrayList<>();
        for (StoryCommentMapper.CommentRow row : rows) {
            List<CommentMention> mentions = mentionsByComment.getOrDefault(row.id(), Collections.emptyList());
            comments.add(toComment(row, mentions));
        }
        return comments;
    }

    public WriteResult updateComment(UUID id, String body, Instant expectedUpdatedAt,
                                     List<MentionInput> mentions, UUID familyId) {
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

    public WriteResult deleteComment(UUID id) {
        int rowsAffected = commentMapper.deleteById(id);

        if (rowsAffected == 0) {
            return new WriteResult.NotFound();
        }
        return new WriteResult.Success();
    }

    public void clear() {
        mentionMapper.deleteAll();
        commentMapper.deleteAll();
    }

    private StoryComment toComment(StoryCommentMapper.CommentRow row, List<CommentMention> mentions) {
        return new StoryComment(
                row.id(),
                row.storyId(),
                row.authorUserId(),
                row.body(),
                row.createdAt(),
                row.updatedAt(),
                mentions
        );
    }
}
