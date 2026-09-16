package com.genealogy.service;

import com.genealogy.domain.family.Role;
import com.genealogy.domain.story.Story;
import com.genealogy.domain.story.StoryComment;
import com.genealogy.store.StoryCommentStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class StoryCommentService {
    public static final int MAX_BODY_LENGTH = 1000;

    private final StoryCommentStore commentStore;
    private final StoryService storyService;

    public StoryCommentService(StoryCommentStore commentStore, StoryService storyService) {
        this.commentStore = commentStore;
        this.storyService = storyService;
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

    @Transactional
    public StoryComment createComment(UUID familyId, UUID storyId, UUID authorUserId,
                                      String body, Role userRole) {
        if (!userRole.canWrite()) {
            throw new PermissionDeniedException("only admin or editor can create comments");
        }

        Optional<Story> storyOpt = storyService.getStory(familyId, storyId, userRole);
        if (storyOpt.isEmpty()) {
            throw new StoryNotFoundException();
        }

        validateBody(body);

        UUID commentId = UUID.randomUUID();
        StoryComment comment = new StoryComment(
                commentId,
                storyId,
                authorUserId,
                body,
                null,
                null
        );

        commentStore.createComment(comment);
        return commentStore.getComment(commentId).orElseThrow(CommentNotFoundException::new);
    }

    @Transactional
    public StoryComment updateComment(UUID familyId, UUID storyId, UUID commentId,
                                      UUID requestingUserId, String body, Instant expectedUpdatedAt,
                                      Role userRole) {
        if (!userRole.canWrite()) {
            throw new PermissionDeniedException("write access required");
        }

        Optional<Story> storyOpt = storyService.getStory(familyId, storyId, userRole);
        if (storyOpt.isEmpty()) {
            throw new StoryNotFoundException();
        }

        Optional<StoryComment> existingOpt = commentStore.getCommentByIdAndStoryId(commentId, storyId);
        if (existingOpt.isEmpty()) {
            throw new CommentNotFoundException();
        }

        StoryComment existing = existingOpt.get();

        if (!existing.getAuthorUserId().equals(requestingUserId)) {
            throw new NotAuthorException();
        }

        validateBody(body);

        StoryCommentStore.WriteResult result = commentStore.updateComment(commentId, body, expectedUpdatedAt);
        if (result instanceof StoryCommentStore.WriteResult.NotFound) {
            throw new CommentNotFoundException();
        } else if (result instanceof StoryCommentStore.WriteResult.VersionConflict conflict) {
            throw new VersionConflictException(conflict.currentComment());
        }

        return commentStore.getComment(commentId).orElseThrow(CommentNotFoundException::new);
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

        Optional<StoryComment> existingOpt = commentStore.getCommentByIdAndStoryId(commentId, storyId);
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

        return commentStore.getCommentByIdAndStoryId(commentId, storyId);
    }

    @Transactional(readOnly = true)
    public List<StoryComment> listComments(UUID familyId, UUID storyId, Role userRole) {
        Optional<Story> storyOpt = storyService.getStory(familyId, storyId, userRole);
        if (storyOpt.isEmpty()) {
            throw new StoryNotFoundException();
        }

        return commentStore.listByStoryId(storyId);
    }

    private void validateBody(String body) {
        if (body == null || body.isBlank()) {
            throw new InvalidBodyException("body is required");
        }
        if (body.length() > MAX_BODY_LENGTH) {
            throw new InvalidBodyException("body exceeds maximum length of " + MAX_BODY_LENGTH + " characters");
        }
    }
}
