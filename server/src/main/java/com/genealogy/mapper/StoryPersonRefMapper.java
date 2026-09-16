package com.genealogy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface StoryPersonRefMapper {

    void insert(@Param("id") UUID id,
                @Param("storyId") UUID storyId,
                @Param("personId") UUID personId,
                @Param("displayNameSnapshot") String displayNameSnapshot);

    List<PersonRefRow> findByStoryId(@Param("storyId") UUID storyId);

    List<PersonRefRow> findByStoryIds(@Param("storyIds") List<UUID> storyIds);

    void deleteByStoryId(@Param("storyId") UUID storyId);

    void deleteAll();

    record PersonRefRow(
            UUID id,
            UUID storyId,
            UUID personId,
            String displayNameSnapshot,
            Instant createdAt
    ) {}
}
