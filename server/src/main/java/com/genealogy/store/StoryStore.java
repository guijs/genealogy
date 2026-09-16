package com.genealogy.store;

import com.genealogy.domain.projection.ProjectionPerson;
import com.genealogy.domain.story.PersonRefStatus;
import com.genealogy.domain.story.Story;
import com.genealogy.domain.story.StoryPersonRef;
import com.genealogy.mapper.PersonMapper;
import com.genealogy.mapper.StoryMapper;
import com.genealogy.mapper.StoryPersonRefMapper;
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

    public Optional<Story> getStory(UUID id) {
        StoryMapper.StoryRow row = storyMapper.findById(id);
        if (row == null) {
            return Optional.empty();
        }
        List<UUID> personIds = storyMapper.findPersonIdsByStoryId(id);
        List<StoryPersonRef> personRefs = fetchPersonRefsForStory(id);
        return Optional.of(toStory(row, personIds, personRefs));
    }

    public Optional<Story> getStoryByIdAndFamilyId(UUID id, UUID familyId) {
        StoryMapper.StoryRow row = storyMapper.findByIdAndFamilyId(id, familyId);
        if (row == null) {
            return Optional.empty();
        }
        List<UUID> personIds = storyMapper.findPersonIdsByStoryId(id);
        List<StoryPersonRef> personRefs = fetchPersonRefsForStory(id);
        return Optional.of(toStory(row, personIds, personRefs));
    }

    public List<Story> listByFamily(UUID familyId) {
        List<StoryMapper.StoryRow> rows = storyMapper.findByFamilyId(familyId);
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> storyIds = rows.stream().map(StoryMapper.StoryRow::id).collect(Collectors.toList());
        Map<UUID, List<StoryPersonRef>> personRefsByStory = fetchPersonRefsForStories(storyIds);

        List<Story> stories = new ArrayList<>();
        for (StoryMapper.StoryRow row : rows) {
            List<UUID> personIds = storyMapper.findPersonIdsByStoryId(row.id());
            List<StoryPersonRef> personRefs = personRefsByStory.getOrDefault(row.id(), Collections.emptyList());
            stories.add(toStory(row, personIds, personRefs));
        }
        return stories;
    }

    public List<Story> listByFamilyAndPerson(UUID familyId, UUID personId) {
        List<StoryMapper.StoryRow> rows = storyMapper.findByFamilyIdAndPersonId(familyId, personId);
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> storyIds = rows.stream().map(StoryMapper.StoryRow::id).collect(Collectors.toList());
        Map<UUID, List<StoryPersonRef>> personRefsByStory = fetchPersonRefsForStories(storyIds);

        List<Story> stories = new ArrayList<>();
        for (StoryMapper.StoryRow row : rows) {
            List<UUID> personIds = storyMapper.findPersonIdsByStoryId(row.id());
            List<StoryPersonRef> personRefs = personRefsByStory.getOrDefault(row.id(), Collections.emptyList());
            stories.add(toStory(row, personIds, personRefs));
        }
        return stories;
    }

    public WriteResult updateStory(Story story, int expectedVersion, List<PersonRefInput> personRefs,
                                   boolean personRefsProvided) {
        int rowsAffected = storyMapper.updateStory(
                story.getId(),
                story.getTitle(),
                story.getBody(),
                story.getNarrativeTime(),
                story.getUpdatedBy(),
                expectedVersion
        );

        if (rowsAffected == 0) {
            Optional<Story> current = getStory(story.getId());
            if (current.isEmpty()) {
                return new WriteResult.NotFound();
            }
            return new WriteResult.VersionConflict(current.get());
        }

        storyMapper.deleteStoryPersons(story.getId());
        for (UUID personId : story.getPersonIds()) {
            storyMapper.insertStoryPerson(story.getId(), personId);
        }

        if (personRefsProvided) {
            personRefMapper.deleteByStoryId(story.getId());
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

        return new WriteResult.Success();
    }

    public WriteResult updateStory(Story story, int expectedVersion) {
        return updateStory(story, expectedVersion, null, false);
    }

    public WriteResult deleteStory(UUID id, int expectedVersion) {
        int rowsAffected = storyMapper.deleteById(id, expectedVersion);

        if (rowsAffected == 0) {
            Optional<Story> current = getStory(id);
            if (current.isEmpty()) {
                return new WriteResult.NotFound();
            }
            return new WriteResult.VersionConflict(current.get());
        }
        return new WriteResult.Success();
    }

    public void clear() {
        personRefMapper.deleteAll();
        storyMapper.deleteAll();
    }

    private List<StoryPersonRef> fetchPersonRefsForStory(UUID storyId) {
        List<StoryPersonRefMapper.PersonRefRow> rows = personRefMapper.findByStoryId(storyId);
        return toPersonRefsWithStatus(rows);
    }

    private Map<UUID, List<StoryPersonRef>> fetchPersonRefsForStories(List<UUID> storyIds) {
        if (storyIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<StoryPersonRefMapper.PersonRefRow> rows = personRefMapper.findByStoryIds(storyIds);
        List<StoryPersonRef> refs = toPersonRefsWithStatus(rows);
        return refs.stream().collect(Collectors.groupingBy(StoryPersonRef::getStoryId));
    }

    private List<StoryPersonRef> toPersonRefsWithStatus(List<StoryPersonRefMapper.PersonRefRow> rows) {
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
                            status = PersonRefStatus.HIDDEN;
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
