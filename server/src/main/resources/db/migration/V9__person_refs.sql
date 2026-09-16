-- V9: Person References feature (P1 MVP)
--
-- Allows referencing family persons (Person, not User) in stories and comments.
-- Separate from @mentions (user_id) - MUST NOT be mixed.
--
-- Rules:
--   - Write: validate person_id is in same family and not hard-deleted → else 400
--   - Read: include refs with visibility status (active/hidden/deleted)
--   - Historical: keep snapshot when person is hard-deleted (ON DELETE SET NULL)
--   - Deceased persons ARE allowed (deathYear != null is OK)
--   - No auto-parse from body text; only submitted person_refs list
--   - No notifications, no privilege elevation

-- Story Person References
CREATE TABLE story_person_refs (
    id UUID PRIMARY KEY,
    story_id UUID NOT NULL,
    person_id UUID,
    display_name_snapshot VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_story_person_refs_story FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE,
    CONSTRAINT fk_story_person_refs_person FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE SET NULL
);

CREATE INDEX idx_story_person_refs_story_id ON story_person_refs(story_id);
CREATE INDEX idx_story_person_refs_person_id ON story_person_refs(person_id);

-- Comment Person References
CREATE TABLE comment_person_refs (
    id UUID PRIMARY KEY,
    comment_id UUID NOT NULL,
    person_id UUID,
    display_name_snapshot VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_comment_person_refs_comment FOREIGN KEY (comment_id) REFERENCES story_comments(id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_person_refs_person FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE SET NULL
);

CREATE INDEX idx_comment_person_refs_comment_id ON comment_person_refs(comment_id);
CREATE INDEX idx_comment_person_refs_person_id ON comment_person_refs(person_id);
