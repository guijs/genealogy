package com.genealogy.service;

import com.genealogy.domain.family.Role;
import com.genealogy.domain.story.Story;
import com.genealogy.domain.story.StoryComment;
import com.genealogy.store.StoryCommentStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public static class InvalidParentException extends RuntimeException {
        public InvalidParentException(String message) {
            super(message);
        }
    }

    public static class PermissionDeniedException extends RuntimeException {
        public PermissionDeniedException(String message) {
            super(message);
        }
    }

    @Transactional
    public StoryComment createComment(UUID familyId, UUID storyId, UUID authorUserId,
                                      String body, UUID parentCommentId, Role userRole) {
        Optional<Story> storyOpt = storyService.getStory(familyId, storyId, userRole);
        if (storyOpt.isEmpty()) {
            throw new StoryNotFoundException();
        }

        validateBody(body);

        if (parentCommentId != null) {
            validateParentComment(storyId, parentCommentId);
        }

        UUID commentId = UUID.randomUUID();
        StoryComment comment = new StoryComment(
                commentId,
                storyId,
                parentCommentId,
                authorUserId,
                body,
                null
        );

        commentStore.createComment(comment);
        return commentStore.getComment(commentId).orElseThrow(CommentNotFoundException::new);
    }

    @Transactional
    public void deleteComment(UUID familyId, UUID storyId, UUID commentId,
                              UUID requestingUserId, Role userRole) {
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
        boolean canWriteOthers = userRole.canWrite();

        if (!isAuthor && !canWriteOthers) {
            throw new PermissionDeniedException("only author or admin/editor can delete comments");
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

    private void validateParentComment(UUID storyId, UUID parentCommentId) {
        Optional<StoryComment> parentOpt = commentStore.getCommentByIdAndStoryId(parentCommentId, storyId);
        if (parentOpt.isEmpty()) {
            throw new InvalidParentException("parent comment not found");
        }

        StoryComment parent = parentOpt.get();
        if (!parent.isRootComment()) {
            throw new InvalidParentException("reply-to-reply not allowed; parent must be a root comment");
        }
    }
}
