package com.genealogy.store;

import com.genealogy.domain.story.Story;
import com.genealogy.mapper.StoryMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class StoryStore {
    private final StoryMapper storyMapper;

    public StoryStore(StoryMapper storyMapper) {
        this.storyMapper = storyMapper;
    }

    public void createStory(Story story) {
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
    }

    public Optional<Story> getStory(UUID id) {
        StoryMapper.StoryRow row = storyMapper.findById(id);
        if (row == null) {
            return Optional.empty();
        }
        List<UUID> personIds = storyMapper.findPersonIdsByStoryId(id);
        return Optional.of(toStory(row, personIds));
    }

    public Optional<Story> getStoryByIdAndFamilyId(UUID id, UUID familyId) {
        StoryMapper.StoryRow row = storyMapper.findByIdAndFamilyId(id, familyId);
        if (row == null) {
            return Optional.empty();
        }
        List<UUID> personIds = storyMapper.findPersonIdsByStoryId(id);
        return Optional.of(toStory(row, personIds));
    }

    public List<Story> listByFamily(UUID familyId) {
        List<StoryMapper.StoryRow> rows = storyMapper.findByFamilyId(familyId);
        List<Story> stories = new ArrayList<>();
        for (StoryMapper.StoryRow row : rows) {
            List<UUID> personIds = storyMapper.findPersonIdsByStoryId(row.id());
            stories.add(toStory(row, personIds));
        }
        return stories;
    }

    public List<Story> listByFamilyAndPerson(UUID familyId, UUID personId) {
        List<StoryMapper.StoryRow> rows = storyMapper.findByFamilyIdAndPersonId(familyId, personId);
        List<Story> stories = new ArrayList<>();
        for (StoryMapper.StoryRow row : rows) {
            List<UUID> personIds = storyMapper.findPersonIdsByStoryId(row.id());
            stories.add(toStory(row, personIds));
        }
        return stories;
    }

    public void updateStory(Story story) {
        storyMapper.updateStory(
                story.getId(),
                story.getTitle(),
                story.getBody(),
                story.getNarrativeTime(),
                story.getUpdatedBy(),
                story.getVersion()
        );
        storyMapper.deleteStoryPersons(story.getId());
        for (UUID personId : story.getPersonIds()) {
            storyMapper.insertStoryPerson(story.getId(), personId);
        }
    }

    public void deleteStory(UUID id) {
        storyMapper.deleteById(id);
    }

    public void clear() {
        storyMapper.deleteAll();
    }

    private Story toStory(StoryMapper.StoryRow row, List<UUID> personIds) {
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
                personIds
        );
    }
}
