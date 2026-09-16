package com.genealogy.store;

import com.genealogy.domain.family.Role;
import com.genealogy.domain.projection.ProjectionPerson;
import com.genealogy.domain.story.PersonRefStatus;
import com.genealogy.domain.story.Story;
import com.genealogy.domain.story.StoryPersonRef;
import com.genealogy.mapper.PersonMapper;
import com.genealogy.mapper.StoryMapper;
import com.genealogy.mapper.StoryPersonRefMapper;
import com.genealogy.service.PersonRefUpdateAction;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class StoryStore {
    private final StoryMapper storyMapper;
    private final StoryPersonRefMapper personRefMapper;
    private final PersonMapper personMapper;

    public StoryStore(StoryMapper storyMapper, StoryPersonRefMapper personRefMapper, PersonMapper personMapper) {
        this.storyMapper = storyMapper;
        this.personRefMapper = personRefMapper;
        this.personMapper = personMapper;
    }

    public record PersonRefInput(UUID personId, String displayNameSnapshot) {}

    public sealed interface WriteResult {
        record Success() implements WriteResult {}
        record NotFound() implements WriteResult {}
        record VersionConflict(Story currentStory) implements WriteResult {}
    }

    public void createStory(Story story, List<PersonRefInput> personRefs) {
        storyMapper.insertStory(
                story.getId(),
                story.getFamilyId(),
                story.getTitle(),
                story.getBody(),
                story.getNarrativeTime(),
                story.getCreatedBy(),
                story.getUpdatedBy()
        );
        for (UUID personId : story.getPersonIds()) {
            storyMapper.insertStoryPerson(story.getId(), personId);
        }
        if (personRefs != null) {
            for (PersonRefInput ref : personRefs) {
                personRefMapper.insert(
                        UUID.randomUUID(),
                        story.getId(),
                        ref.personId(),
                        ref.displayNameSnapshot()
                );
            }
        }
    }

    public void createStory(Story story) {
        createStory(story, null);
    }

    public Optional<Story> getStory(UUID id, Role userRole) {
        StoryMapper.StoryRow row = storyMapper.findById(id);
        if (row == null) {
            return Optional.empty();
        }
        List<UUID> personIds = storyMapper.findPersonIdsByStoryId(id);
        List<StoryPersonRef> personRefs = fetchPersonRefsForStory(id, userRole);
        return Optional.of(toStory(row, personIds, personRefs));
    }

    public Optional<Story> getStory(UUID id) {
        return getStory(id, Role.VIEWER);
    }

    public Optional<Story> getStoryByIdAndFamilyId(UUID id, UUID familyId, Role userRole) {
        StoryMapper.StoryRow row = storyMapper.findByIdAndFamilyId(id, familyId);
        if (row == null) {
            return Optional.empty();
        }
        List<UUID> personIds = storyMapper.findPersonIdsByStoryId(id);
        List<StoryPersonRef> personRefs = fetchPersonRefsForStory(id, userRole);
        return Optional.of(toStory(row, personIds, personRefs));
    }

    public Optional<Story> getStoryByIdAndFamilyId(UUID id, UUID familyId) {
        return getStoryByIdAndFamilyId(id, familyId, Role.VIEWER);
    }

    public List<Story> listByFamily(UUID familyId, Role userRole) {
        List<StoryMapper.StoryRow> rows = storyMapper.findByFamilyId(familyId);
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> storyIds = rows.stream().map(StoryMapper.StoryRow::id).collect(Collectors.toList());
        Map<UUID, List<StoryPersonRef>> personRefsByStory = fetchPersonRefsForStories(storyIds, userRole);

        List<Story> stories = new ArrayList<>();
        for (StoryMapper.StoryRow row : rows) {
            List<UUID> personIds = storyMapper.findPersonIdsByStoryId(row.id());
            List<StoryPersonRef> personRefs = personRefsByStory.getOrDefault(row.id(), Collections.emptyList());
            stories.add(toStory(row, personIds, personRefs));
        }
        return stories;
    }

    public List<Story> listByFamily(UUID familyId) {
        return listByFamily(familyId, Role.VIEWER);
    }

    public List<Story> listByFamilyAndPerson(UUID familyId, UUID personId, Role userRole) {
        List<StoryMapper.StoryRow> rows = storyMapper.findByFamilyIdAndPersonId(familyId, personId);
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> storyIds = rows.stream().map(StoryMapper.StoryRow::id).collect(Collectors.toList());
        Map<UUID, List<StoryPersonRef>> personRefsByStory = fetchPersonRefsForStories(storyIds, userRole);

        List<Story> stories = new ArrayList<>();
        for (StoryMapper.StoryRow row : rows) {
            List<UUID> personIds = storyMapper.findPersonIdsByStoryId(row.id());
            List<StoryPersonRef> personRefs = personRefsByStory.getOrDefault(row.id(), Collections.emptyList());
            stories.add(toStory(row, personIds, personRefs));
        }
        return stories;
    }

    public List<Story> listByFamilyAndPerson(UUID familyId, UUID personId) {
        return listByFamilyAndPerson(familyId, personId, Role.VIEWER);
    }

    public WriteResult updateStory(Story story, int expectedVersion, PersonRefUpdateAction personRefAction, Role userRole) {
        int rowsAffected = storyMapper.updateStory(
                story.getId(),
                story.getTitle(),
                story.getBody(),
                story.getNarrativeTime(),
                story.getUpdatedBy(),
                expectedVersion
        );

        if (rowsAffected == 0) {
            Optional<Story> current = getStory(story.getId(), userRole);
            if (current.isEmpty()) {
                return new WriteResult.NotFound();
            }
            return new WriteResult.VersionConflict(current.get());
        }

        storyMapper.deleteStoryPersons(story.getId());
        for (UUID personId : story.getPersonIds()) {
            storyMapper.insertStoryPerson(story.getId(), personId);
        }

        if (personRefAction instanceof PersonRefUpdateAction.Omit) {
            // Do not touch existing person_refs
        } else if (personRefAction instanceof PersonRefUpdateAction.ClearAll) {
            // Clear all person_refs
            personRefMapper.deleteByStoryId(story.getId());
        } else if (personRefAction instanceof PersonRefUpdateAction.Replace replace) {
            // Replace all person_refs
            personRefMapper.deleteByStoryId(story.getId());
            for (PersonRefUpdateAction.Replace.PersonRefInput ref : replace.personRefs()) {
                personRefMapper.insert(
                        UUID.randomUUID(),
                        story.getId(),
                        ref.personId(),
                        ref.displayNameSnapshot()
                );
            }
        }

        return new WriteResult.Success();
    }

    public WriteResult updateStory(Story story, int expectedVersion) {
        return updateStory(story, expectedVersion, new PersonRefUpdateAction.Omit(), Role.VIEWER);
    }

    public WriteResult deleteStory(UUID id, int expectedVersion, Role userRole) {
        int rowsAffected = storyMapper.deleteById(id, expectedVersion);

        if (rowsAffected == 0) {
            Optional<Story> current = getStory(id, userRole);
            if (current.isEmpty()) {
                return new WriteResult.NotFound();
            }
            return new WriteResult.VersionConflict(current.get());
        }
        return new WriteResult.Success();
    }

    public WriteResult deleteStory(UUID id, int expectedVersion) {
        return deleteStory(id, expectedVersion, Role.VIEWER);
    }

    public void clear() {
        personRefMapper.deleteAll();
        storyMapper.deleteAll();
    }

    private List<StoryPersonRef> fetchPersonRefsForStory(UUID storyId, Role userRole) {
        List<StoryPersonRefMapper.PersonRefRow> rows = personRefMapper.findByStoryId(storyId);
        return toPersonRefsWithStatus(rows, userRole);
    }

    private Map<UUID, List<StoryPersonRef>> fetchPersonRefsForStories(List<UUID> storyIds, Role userRole) {
        if (storyIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<StoryPersonRefMapper.PersonRefRow> rows = personRefMapper.findByStoryIds(storyIds);
        List<StoryPersonRef> refs = toPersonRefsWithStatus(rows, userRole);
        return refs.stream().collect(Collectors.groupingBy(StoryPersonRef::getStoryId));
    }

    private List<StoryPersonRef> toPersonRefsWithStatus(List<StoryPersonRefMapper.PersonRefRow> rows, Role userRole) {
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        Set<UUID> personIds = rows.stream()
                .map(StoryPersonRefMapper.PersonRefRow::personId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, ProjectionPerson> personMap = new HashMap<>();
        for (UUID personId : personIds) {
            ProjectionPerson person = personMapper.findProjectionById(personId);
            if (person != null) {
                personMap.put(personId, person);
            }
        }

        return rows.stream()
                .map(row -> {
                    PersonRefStatus status;
                    if (row.personId() == null) {
                        status = PersonRefStatus.DELETED;
                    } else {
                        ProjectionPerson person = personMap.get(row.personId());
                        if (person == null) {
                            status = PersonRefStatus.DELETED;
                        } else if (person.isHidden()) {
                            // Hidden person: visible to admin/editor, hidden to viewer
                            if (userRole.canWrite()) {
                                status = PersonRefStatus.ACTIVE;
                            } else {
                                status = PersonRefStatus.HIDDEN;
                            }
                        } else {
                            status = PersonRefStatus.ACTIVE;
                        }
                    }
                    return new StoryPersonRef(
                            row.id(),
                            row.storyId(),
                            row.personId(),
                            row.displayNameSnapshot(),
                            row.createdAt(),
                            status
                    );
                })
                .collect(Collectors.toList());
    }

    private Story toStory(StoryMapper.StoryRow row, List<UUID> personIds, List<StoryPersonRef> personRefs) {
        return new Story(
                row.id(),
                row.familyId(),
                row.title(),
                row.body(),
                row.narrativeTime(),
                row.createdBy(),
                row.updatedBy(),
                row.createdAt(),
                row.updatedAt(),
                row.version() != null ? row.version() : 1,
                personIds,
                personRefs
        );
    }
}
