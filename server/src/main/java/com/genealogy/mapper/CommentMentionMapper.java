package com.genealogy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface CommentMentionMapper {

    void insert(@Param("id") UUID id,
                @Param("commentId") UUID commentId,
                @Param("userId") UUID userId,
                @Param("displayNameSnapshot") String displayNameSnapshot);

    List<MentionRow> findByCommentId(@Param("commentId") UUID commentId);

    List<MentionRow> findByCommentIds(@Param("commentIds") List<UUID> commentIds);

    void deleteByCommentId(@Param("commentId") UUID commentId);

    void deleteByCommentIdAndUserIds(@Param("commentId") UUID commentId, @Param("userIds") List<UUID> userIds);

    void deleteAll();

    record MentionRow(
            UUID id,
            UUID commentId,
            UUID userId,
            String displayNameSnapshot,
            Instant createdAt
    ) {}
}
