package com.genealogy.store;

import com.genealogy.domain.story.StoryComment;
import com.genealogy.mapper.StoryCommentMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class StoryCommentStore {
    private final StoryCommentMapper commentMapper;

    public StoryCommentStore(StoryCommentMapper commentMapper) {
        this.commentMapper = commentMapper;
    }

    public sealed interface WriteResult {
        record Success() implements WriteResult {}
        record NotFound() implements WriteResult {}
    }

    public void createComment(StoryComment comment) {
        commentMapper.insertComment(
                comment.getId(),
                comment.getStoryId(),
                comment.getParentCommentId(),
                comment.getAuthorUserId(),
                comment.getBody()
        );
    }

    public Optional<StoryComment> getComment(UUID id) {
        StoryCommentMapper.CommentRow row = commentMapper.findById(id);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(toComment(row));
    }

    public Optional<StoryComment> getCommentByIdAndStoryId(UUID id, UUID storyId) {
        StoryCommentMapper.CommentRow row = commentMapper.findByIdAndStoryId(id, storyId);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(toComment(row));
    }

    public List<StoryComment> listByStoryId(UUID storyId) {
        List<StoryCommentMapper.CommentRow> rows = commentMapper.findByStoryId(storyId);
        List<StoryComment> comments = new ArrayList<>();
        for (StoryCommentMapper.CommentRow row : rows) {
            comments.add(toComment(row));
        }
        return comments;
    }

    public WriteResult deleteComment(UUID id) {
        int rowsAffected = commentMapper.deleteById(id);

        if (rowsAffected == 0) {
            return new WriteResult.NotFound();
        }
        return new WriteResult.Success();
    }

    public void clear() {
        commentMapper.deleteAll();
    }

    private StoryComment toComment(StoryCommentMapper.CommentRow row) {
        return new StoryComment(
                row.id(),
                row.storyId(),
                row.parentCommentId(),
                row.authorUserId(),
                row.body(),
                row.createdAt()
        );
    }
}
