package com.genealogy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface CommentPersonRefMapper {

    void insert(@Param("id") UUID id,
                @Param("commentId") UUID commentId,
                @Param("personId") UUID personId,
                @Param("displayNameSnapshot") String displayNameSnapshot);

    List<PersonRefRow> findByCommentId(@Param("commentId") UUID commentId);

    List<PersonRefRow> findByCommentIds(@Param("commentIds") List<UUID> commentIds);

    void deleteByCommentId(@Param("commentId") UUID commentId);

    void deleteAll();

    record PersonRefRow(
            UUID id,
            UUID commentId,
            UUID personId,
            String displayNameSnapshot,
            Instant createdAt
    ) {}
}
