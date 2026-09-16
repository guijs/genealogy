-- V6: Story Comments feature (① schedule)
-- Depth ≤ 2: root comments + one reply level only
--
-- Access rules:
--   - All member roles (admin/editor/viewer) may post comments
--   - Author may delete own comment
--   - Delete others = admin + editor only (canWrite)
--   - No edit endpoint (no PUT/PATCH)
--   - No optimistic lock on comments
--
-- Visibility: follows story visibility (Hidden rules)
-- Story hard-delete: CASCADE deletes all comments

CREATE TABLE story_comments (
    id UUID PRIMARY KEY,
    story_id UUID NOT NULL,
    parent_comment_id UUID,
    author_user_id UUID NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_story_comments_story FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE,
    CONSTRAINT fk_story_comments_parent FOREIGN KEY (parent_comment_id) REFERENCES story_comments(id) ON DELETE CASCADE,
    CONSTRAINT chk_story_comments_body_length CHECK (LENGTH(body) <= 1000)
);

CREATE INDEX idx_story_comments_story_id ON story_comments(story_id);
CREATE INDEX idx_story_comments_parent_id ON story_comments(parent_comment_id);
CREATE INDEX idx_story_comments_author_user_id ON story_comments(author_user_id);
CREATE INDEX idx_story_comments_created_at ON story_comments(story_id, created_at);
