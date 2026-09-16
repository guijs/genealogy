package com.genealogy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Mapper
public interface StoryMapper {

    void insertStory(@Param("id") UUID id,
                     @Param("familyId") UUID familyId,
                     @Param("title") String title,
                     @Param("body") String body,
                     @Param("narrativeTime") LocalDate narrativeTime,
                     @Param("createdBy") UUID createdBy,
                     @Param("updatedBy") UUID updatedBy);

    void insertStoryPerson(@Param("storyId") UUID storyId,
                           @Param("personId") UUID personId);

    void deleteStoryPersons(@Param("storyId") UUID storyId);

    int updateStory(@Param("id") UUID id,
                    @Param("title") String title,
                    @Param("body") String body,
                    @Param("narrativeTime") LocalDate narrativeTime,
                    @Param("updatedBy") UUID updatedBy,
                    @Param("expectedVersion") int expectedVersion);

    StoryRow findById(@Param("id") UUID id);

    StoryRow findByIdAndFamilyId(@Param("id") UUID id, @Param("familyId") UUID familyId);

    List<StoryRow> findByFamilyId(@Param("familyId") UUID familyId);

    List<StoryRow> findByFamilyIdAndPersonId(@Param("familyId") UUID familyId, @Param("personId") UUID personId);

    List<UUID> findPersonIdsByStoryId(@Param("storyId") UUID storyId);

    int deleteById(@Param("id") UUID id, @Param("expectedVersion") int expectedVersion);

    void deleteAll();

    record StoryRow(
            UUID id,
            UUID familyId,
            String title,
            String body,
            LocalDate narrativeTime,
            UUID createdBy,
            UUID updatedBy,
            Instant createdAt,
            Instant updatedAt,
            Integer version
    ) {}
}
