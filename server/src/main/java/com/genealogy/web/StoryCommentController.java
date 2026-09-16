package com.genealogy.web;

import com.genealogy.domain.family.Membership;
import com.genealogy.domain.story.StoryComment;
import com.genealogy.service.StoryCommentService;
import com.genealogy.web.dto.CommentResponse;
import com.genealogy.web.dto.CommentsListResponse;
import com.genealogy.web.dto.CreateCommentRequest;
import com.genealogy.web.dto.ErrorResponse;
import com.genealogy.web.filter.FamilyMembershipFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/families/{familyId}/stories/{storyId}/comments")
public class StoryCommentController {

    private final StoryCommentService commentService;

    public StoryCommentController(StoryCommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    public ResponseEntity<?> listComments(HttpServletRequest request,
                                          @PathVariable String storyId) {
        UUID familyId = (UUID) request.getAttribute(FamilyMembershipFilter.FAMILY_ID_ATTRIBUTE);
        if (familyId == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        Membership membership = (Membership) request.getAttribute(FamilyMembershipFilter.MEMBERSHIP_ATTRIBUTE);
        if (membership == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        UUID storyUUID;
        try {
            storyUUID = UUID.fromString(storyId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        try {
            List<StoryComment> comments = commentService.listComments(familyId, storyUUID, membership.getRole());
            List<CommentResponse> responses = comments.stream()
                    .map(CommentResponse::fromComment)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(new CommentsListResponse(responses));
        } catch (StoryCommentService.StoryNotFoundException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }
    }

    @GetMapping("/{commentId}")
    public ResponseEntity<?> getComment(HttpServletRequest request,
                                        @PathVariable String storyId,
                                        @PathVariable String commentId) {
        UUID familyId = (UUID) request.getAttribute(FamilyMembershipFilter.FAMILY_ID_ATTRIBUTE);
        if (familyId == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        Membership membership = (Membership) request.getAttribute(FamilyMembershipFilter.MEMBERSHIP_ATTRIBUTE);
        if (membership == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        UUID storyUUID;
        UUID commentUUID;
        try {
            storyUUID = UUID.fromString(storyId);
            commentUUID = UUID.fromString(commentId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        return commentService.getComment(familyId, storyUUID, commentUUID, membership.getRole())
                .<ResponseEntity<?>>map(comment -> ResponseEntity.ok(CommentResponse.fromComment(comment)))
                .orElseGet(() -> ResponseEntity.status(404).body(new ErrorResponse("not found")));
    }

    @PostMapping
    public ResponseEntity<?> createComment(HttpServletRequest request,
                                           @PathVariable String storyId,
                                           @RequestBody CreateCommentRequest body) {
        UUID familyId = (UUID) request.getAttribute(FamilyMembershipFilter.FAMILY_ID_ATTRIBUTE);
        if (familyId == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        Membership membership = (Membership) request.getAttribute(FamilyMembershipFilter.MEMBERSHIP_ATTRIBUTE);
        if (membership == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        UUID storyUUID;
        try {
            storyUUID = UUID.fromString(storyId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        UUID parentCommentId = null;
        if (body.getParentCommentId() != null && !body.getParentCommentId().isBlank()) {
            try {
                parentCommentId = UUID.fromString(body.getParentCommentId());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(new ErrorResponse("invalid parent_comment_id"));
            }
        }

        try {
            StoryComment comment = commentService.createComment(
                    familyId,
                    storyUUID,
                    membership.getUserId(),
                    body.getBody(),
                    parentCommentId,
                    membership.getRole()
            );
            return ResponseEntity.status(201).body(CommentResponse.fromComment(comment));
        } catch (StoryCommentService.StoryNotFoundException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        } catch (StoryCommentService.InvalidBodyException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (StoryCommentService.InvalidParentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<?> deleteComment(HttpServletRequest request,
                                           @PathVariable String storyId,
                                           @PathVariable String commentId) {
        UUID familyId = (UUID) request.getAttribute(FamilyMembershipFilter.FAMILY_ID_ATTRIBUTE);
        if (familyId == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        Membership membership = (Membership) request.getAttribute(FamilyMembershipFilter.MEMBERSHIP_ATTRIBUTE);
        if (membership == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        UUID storyUUID;
        UUID commentUUID;
        try {
            storyUUID = UUID.fromString(storyId);
            commentUUID = UUID.fromString(commentId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        try {
            commentService.deleteComment(
                    familyId,
                    storyUUID,
                    commentUUID,
                    membership.getUserId(),
                    membership.getRole()
            );
            return ResponseEntity.noContent().build();
        } catch (StoryCommentService.StoryNotFoundException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        } catch (StoryCommentService.CommentNotFoundException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        } catch (StoryCommentService.PermissionDeniedException e) {
            return ResponseEntity.status(403).body(new ErrorResponse(e.getMessage()));
        }
    }
}
