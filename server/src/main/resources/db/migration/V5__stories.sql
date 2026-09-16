-- V5: Family Stories feature
-- Tables: stories, story_persons
--
-- Mount semantics:
--   - Family-scoped: story has zero rows in story_persons (applies to entire family)
--   - Person-linked: story has 1..N rows in story_persons (linked to specific persons)
--
-- Person hard-delete behavior:
--   - When a person is deleted, the FK CASCADE on story_persons unbinds that person
--   - This does NOT delete the story row/body
--   - If a story becomes unbound from all persons (zero rows), it becomes family-scoped
--   - Stories are never cascade-deleted due to person deletion

-- Stories table
CREATE TABLE stories (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL,
    title VARCHAR(500),
    body TEXT NOT NULL,
    narrative_time DATE,
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT fk_stories_family FOREIGN KEY (family_id) REFERENCES families(id) ON DELETE CASCADE,
    CONSTRAINT chk_stories_body_length CHECK (LENGTH(body) <= 10000)
);

CREATE INDEX idx_stories_family_id ON stories(family_id);
CREATE INDEX idx_stories_created_by ON stories(created_by);

-- Story-Person join table (N:M for multi-person mount)
-- Empty set = family-scoped story
--
-- ON DELETE CASCADE on person_id: When a person is deleted, this junction row
-- is removed (unbinding the person from the story). The story itself is NOT deleted.
-- If the story ends up with zero persons, it becomes family-scoped.
CREATE TABLE story_persons (
    story_id UUID NOT NULL,
    person_id UUID NOT NULL,
    PRIMARY KEY (story_id, person_id),
    CONSTRAINT fk_story_persons_story FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE,
    CONSTRAINT fk_story_persons_person FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE
);

CREATE INDEX idx_story_persons_story_id ON story_persons(story_id);
CREATE INDEX idx_story_persons_person_id ON story_persons(person_id);
