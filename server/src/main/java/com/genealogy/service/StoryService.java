package com.genealogy.service;

import com.genealogy.domain.family.Role;
import com.genealogy.domain.projection.ProjectionPerson;
import com.genealogy.domain.story.Story;
import com.genealogy.store.PersonStore;
import com.genealogy.store.ProjectionStore;
import com.genealogy.store.StoryStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StoryService {
    public static final int MAX_BODY_LENGTH = 10000;

    private final StoryStore storyStore;
    private final PersonStore personStore;
    private final ProjectionStore projectionStore;

    public StoryService(StoryStore storyStore, PersonStore personStore, ProjectionStore projectionStore) {
        this.storyStore = storyStore;
        this.personStore = personStore;
        this.projectionStore = projectionStore;
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

    public static class InvalidPersonException extends RuntimeException {
        public InvalidPersonException(String message) {
            super(message);
        }
    }

    public static class VersionConflictException extends RuntimeException {
        private final Story currentStory;

        public VersionConflictException(Story currentStory) {
            super("version conflict");
            this.currentStory = currentStory;
        }

        public Story getCurrentStory() {
            return currentStory;
        }
    }

    public static class WriteAccessDeniedException extends RuntimeException {
        public WriteAccessDeniedException() {
            super("write access required");
        }
    }

    @Transactional
    public Story createStory(UUID familyId, UUID creatorId, String title, String body,
                             LocalDate narrativeTime, List<UUID> personIds) {
        validateBody(body);
        validatePersonIds(familyId, personIds);

        UUID storyId = UUID.randomUUID();
        Story story = new Story(
                storyId,
                familyId,
                title,
                body,
                narrativeTime,
                creatorId,
                creatorId,
                null,
                null,
                1,
                personIds != null ? personIds : List.of()
        );

        storyStore.createStory(story);
        return storyStore.getStory(storyId).orElseThrow(StoryNotFoundException::new);
    }

    @Transactional
    public Story updateStory(UUID familyId, UUID storyId, UUID updaterId, Role role,
                             String title, String body, LocalDate narrativeTime,
                             List<UUID> personIds, int expectedVersion) {
        if (role == null || !role.canWrite()) {
            throw new WriteAccessDeniedException();
        }

        Optional<Story> existingOpt = storyStore.getStoryByIdAndFamilyId(storyId, familyId);
        if (existingOpt.isEmpty()) {
            throw new StoryNotFoundException();
        }

        Story existing = existingOpt.get();

        if (existing.getVersion() != expectedVersion) {
            throw new VersionConflictException(existing);
        }

        validateBody(body);
        validatePersonIds(familyId, personIds);

        Story updated = new Story(
                storyId,
                familyId,
                title,
                body,
                narrativeTime,
                existing.getCreatedBy(),
                updaterId,
                existing.getCreatedAt(),
                null,
                expectedVersion + 1,
                personIds != null ? personIds : List.of()
        );

        StoryStore.WriteResult result = storyStore.updateStory(updated, expectedVersion);
        if (result instanceof StoryStore.WriteResult.NotFound) {
            throw new StoryNotFoundException();
        } else if (result instanceof StoryStore.WriteResult.VersionConflict conflict) {
            throw new VersionConflictException(conflict.currentStory());
        }

        return storyStore.getStory(storyId).orElseThrow(StoryNotFoundException::new);
    }

    @Transactional
    public void deleteStory(UUID familyId, UUID storyId, Role role, int expectedVersion) {
        if (role == null || !role.canWrite()) {
            throw new WriteAccessDeniedException();
        }

        Optional<Story> existingOpt = storyStore.getStoryByIdAndFamilyId(storyId, familyId);
        if (existingOpt.isEmpty()) {
            throw new StoryNotFoundException();
        }

        Story existing = existingOpt.get();
        if (existing.getVersion() != expectedVersion) {
            throw new VersionConflictException(existing);
        }

        StoryStore.WriteResult result = storyStore.deleteStory(storyId, expectedVersion);
        if (result instanceof StoryStore.WriteResult.NotFound) {
            throw new StoryNotFoundException();
        } else if (result instanceof StoryStore.WriteResult.VersionConflict conflict) {
            throw new VersionConflictException(conflict.currentStory());
        }
    }

    @Transactional(readOnly = true)
    public Optional<Story> getStory(UUID familyId, UUID storyId, Role userRole) {
        Optional<Story> storyOpt = storyStore.getStoryByIdAndFamilyId(storyId, familyId);
        if (storyOpt.isEmpty()) {
            return Optional.empty();
        }

        Story story = storyOpt.get();

        if (!isVisibleToRole(story, userRole)) {
            return Optional.empty();
        }

        return Optional.of(story);
    }

    @Transactional(readOnly = true)
    public List<Story> listStories(UUID familyId, UUID personIdFilter, Role userRole) {
        List<Story> stories;
        if (personIdFilter != null) {
            stories = storyStore.listByFamilyAndPerson(familyId, personIdFilter);
        } else {
            stories = storyStore.listByFamily(familyId);
        }

        return stories.stream()
                .filter(s -> isVisibleToRole(s, userRole))
                .collect(Collectors.toList());
    }

    private boolean isVisibleToRole(Story story, Role role) {
        if (role.canWrite()) {
            return true;
        }

        for (UUID personId : story.getPersonIds()) {
            Optional<ProjectionPerson> personOpt = projectionStore.getPerson(personId);
            if (personOpt.isPresent() && personOpt.get().isHidden()) {
                return false;
            }
        }
        return true;
    }

    private void validateBody(String body) {
        if (body == null || body.isBlank()) {
            throw new InvalidBodyException("body is required");
        }
        if (body.length() > MAX_BODY_LENGTH) {
            throw new InvalidBodyException("body exceeds maximum length of " + MAX_BODY_LENGTH + " characters");
        }
    }

    private void validatePersonIds(UUID familyId, List<UUID> personIds) {
        if (personIds == null || personIds.isEmpty()) {
            return;
        }

        Set<UUID> uniquePersonIds = new HashSet<>(personIds);
        if (uniquePersonIds.size() != personIds.size()) {
            throw new InvalidPersonException("duplicate person_ids not allowed");
        }

        for (UUID personId : personIds) {
            if (!personStore.existsInFamily(personId, familyId)) {
                throw new InvalidPersonException("person " + personId + " not found in family");
            }
        }
    }
}
