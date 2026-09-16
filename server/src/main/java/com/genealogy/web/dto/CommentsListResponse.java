package com.genealogy.web.dto;

import java.util.List;

public class CommentsListResponse {
    private final List<CommentResponse> comments;

    public CommentsListResponse(List<CommentResponse> comments) {
        this.comments = comments;
    }

    public List<CommentResponse> getComments() {
        return comments;
    }
}
