package com.genealogy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface StoryCommentMapper {

    void insertComment(@Param("id") UUID id,
                       @Param("storyId") UUID storyId,
                       @Param("parentCommentId") UUID parentCommentId,
                       @Param("authorUserId") UUID authorUserId,
                       @Param("body") String body);

    CommentRow findById(@Param("id") UUID id);

    CommentRow findByIdAndStoryId(@Param("id") UUID id, @Param("storyId") UUID storyId);

    List<CommentRow> findByStoryId(@Param("storyId") UUID storyId);

    int deleteById(@Param("id") UUID id);

    void deleteByStoryId(@Param("storyId") UUID storyId);

    void deleteAll();

    record CommentRow(
            UUID id,
            UUID storyId,
            UUID parentCommentId,
            UUID authorUserId,
            String body,
            Instant createdAt
    ) {}
}
