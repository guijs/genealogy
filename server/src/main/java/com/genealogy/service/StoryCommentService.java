package com.genealogy.service;

import com.genealogy.domain.family.Role;
import com.genealogy.domain.story.Story;
import com.genealogy.domain.story.StoryComment;
import com.genealogy.store.FamilyStore;
import com.genealogy.store.StoryCommentStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StoryCommentService {
    public static final int MAX_BODY_LENGTH = 1000;

    private final StoryCommentStore commentStore;
    private final StoryService storyService;
    private final FamilyStore familyStore;

    public StoryCommentService(StoryCommentStore commentStore, StoryService storyService, FamilyStore familyStore) {
        this.commentStore = commentStore;
        this.storyService = storyService;
        this.familyStore = familyStore;
    }

    public static class CommentNotFoundException extends RuntimeException {
        public CommentNotFoundException() {
            super("comment not found");
        }
    }

    public static class StoryNotFoundException extends RuntimeException {
        public StoryNotFoundException() {
            super("story not found");
        }
    }

    public static class InvalidBodyException extends RuntimeException {
        public InvalidBodyException(String message) {
            super(message);
        }
    }

    public static class VersionConflictException extends RuntimeException {
        private final StoryComment currentComment;

        public VersionConflictException(StoryComment currentComment) {
            super("version conflict");
            this.currentComment = currentComment;
        }

        public StoryComment getCurrentComment() {
            return currentComment;
        }
    }

    public static class NotAuthorException extends RuntimeException {
        public NotAuthorException() {
            super("only author can perform this action");
        }
    }

    public static class PermissionDeniedException extends RuntimeException {
        public PermissionDeniedException(String message) {
            super(message);
        }
    }

    public static class InvalidMentionException extends RuntimeException {
        public InvalidMentionException(String message) {
            super(message);
        }
    }

    public record MentionInput(UUID userId, String displayNameSnapshot) {}

    @Transactional
    public StoryComment createComment(UUID familyId, UUID storyId, UUID authorUserId,
                                      String body, List<MentionInput> mentions, Role userRole) {
        if (!userRole.canWrite()) {
            throw new PermissionDeniedException("only admin or editor can create comments");
        }

        Optional<Story> storyOpt = storyService.getStory(familyId, storyId, userRole);
        if (storyOpt.isEmpty()) {
            throw new StoryNotFoundException();
        }

        validateBody(body);
        validateMentions(familyId, mentions);

        UUID commentId = UUID.randomUUID();
        StoryComment comment = new StoryComment(
                commentId,
                storyId,
                authorUserId,
                body,
                null,
                null
        );

        List<StoryCommentStore.MentionInput> storeMentions = mentions != null
                ? mentions.stream()
                    .map(m -> new StoryCommentStore.MentionInput(m.userId(), m.displayNameSnapshot()))
                    .collect(Collectors.toList())
                : null;

        commentStore.createComment(comment, storeMentions);
        return commentStore.getComment(commentId, familyId).orElseThrow(CommentNotFoundException::new);
    }

    @Transactional
    public StoryComment updateComment(UUID familyId, UUID storyId, UUID commentId,
                                      UUID requestingUserId, String body, MentionUpdateAction mentionAction,
                                      Instant expectedUpdatedAt, Role userRole) {
        if (!userRole.canWrite()) {
            throw new PermissionDeniedException("write access required");
        }

        Optional<Story> storyOpt = storyService.getStory(familyId, storyId, userRole);
        if (storyOpt.isEmpty()) {
            throw new StoryNotFoundException();
        }

        Optional<StoryComment> existingOpt = commentStore.getCommentByIdAndStoryId(commentId, storyId, familyId);
        if (existingOpt.isEmpty()) {
            throw new CommentNotFoundException();
        }

        StoryComment existing = existingOpt.get();

        if (!existing.getAuthorUserId().equals(requestingUserId)) {
            throw new NotAuthorException();
        }

        validateBody(body);

        if (mentionAction instanceof MentionUpdateAction.Replace replace) {
            validateMentionsForUpdate(familyId, replace.mentions());
        }

        StoryCommentStore.WriteResult result = commentStore.updateComment(
                commentId, body, expectedUpdatedAt, mentionAction, familyId);
        if (result instanceof StoryCommentStore.WriteResult.NotFound) {
            throw new CommentNotFoundException();
        } else if (result instanceof StoryCommentStore.WriteResult.VersionConflict conflict) {
            throw new VersionConflictException(conflict.currentComment());
        }

        return commentStore.getComment(commentId, familyId).orElseThrow(CommentNotFoundException::new);
    }

    @Transactional
    public void deleteComment(UUID familyId, UUID storyId, UUID commentId,
                              UUID requestingUserId, Role userRole) {
        if (!userRole.canWrite()) {
            throw new PermissionDeniedException("write access required");
        }

        Optional<Story> storyOpt = storyService.getStory(familyId, storyId, userRole);
        if (storyOpt.isEmpty()) {
            throw new StoryNotFoundException();
        }

        Optional<StoryComment> existingOpt = commentStore.getCommentByIdAndStoryId(commentId, storyId, familyId);
        if (existingOpt.isEmpty()) {
            throw new CommentNotFoundException();
        }

        StoryComment existing = existingOpt.get();
        boolean isAuthor = existing.getAuthorUserId().equals(requestingUserId);
        boolean isAdmin = userRole == Role.ADMIN;

        if (!isAuthor && !isAdmin) {
            throw new PermissionDeniedException("only author or admin can delete comments");
        }

        StoryCommentStore.WriteResult result = commentStore.deleteComment(commentId);
        if (result instanceof StoryCommentStore.WriteResult.NotFound) {
            throw new CommentNotFoundException();
        }
    }

    @Transactional(readOnly = true)
    public Optional<StoryComment> getComment(UUID familyId, UUID storyId, UUID commentId, Role userRole) {
        Optional<Story> storyOpt = storyService.getStory(familyId, storyId, userRole);
        if (storyOpt.isEmpty()) {
            return Optional.empty();
        }

        return commentStore.getCommentByIdAndStoryId(commentId, storyId, familyId);
    }

    @Transactional(readOnly = true)
    public List<StoryComment> listComments(UUID familyId, UUID storyId, Role userRole) {
        Optional<Story> storyOpt = storyService.getStory(familyId, storyId, userRole);
        if (storyOpt.isEmpty()) {
            throw new StoryNotFoundException();
        }

        return commentStore.listByStoryId(storyId, familyId);
    }

    private void validateBody(String body) {
        if (body == null || body.isBlank()) {
            throw new InvalidBodyException("body is required");
        }
        if (body.length() > MAX_BODY_LENGTH) {
            throw new InvalidBodyException("body exceeds maximum length of " + MAX_BODY_LENGTH + " characters");
        }
    }

    private void validateMentions(UUID familyId, List<MentionInput> mentions) {
        if (mentions == null || mentions.isEmpty()) {
            return;
        }

        for (MentionInput mention : mentions) {
            if (mention.userId() == null) {
                throw new InvalidMentionException("mention user_id is required");
            }
            if (mention.displayNameSnapshot() == null || mention.displayNameSnapshot().isBlank()) {
                throw new InvalidMentionException("mention display_name_snapshot is required");
            }
            if (!familyStore.isMember(familyId, mention.userId())) {
                throw new InvalidMentionException("mentioned user is not a current family member");
            }
        }
    }

    /**
     * Validate mentions for update action (Replace).
     * All user_ids in the request must be current family members.
     */
    private void validateMentionsForUpdate(UUID familyId, List<MentionUpdateAction.Replace.MentionInput> mentions) {
        if (mentions == null || mentions.isEmpty()) {
            return;
        }

        for (MentionUpdateAction.Replace.MentionInput mention : mentions) {
            if (mention.userId() == null) {
                throw new InvalidMentionException("mention user_id is required");
            }
            if (mention.displayNameSnapshot() == null || mention.displayNameSnapshot().isBlank()) {
                throw new InvalidMentionException("mention display_name_snapshot is required");
            }
            if (!familyStore.isMember(familyId, mention.userId())) {
                throw new InvalidMentionException("mentioned user is not a current family member");
            }
        }
    }
}
