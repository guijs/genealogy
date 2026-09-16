-- V6: Story Comments feature (v0.2 frozen)
-- Flat comments only (no parent_comment_id, no replies)
--
-- Access rules:
--   - viewer: read-only (cannot create/edit/delete)
--   - admin/editor: can create
--   - author: can edit own (with conditional write 409), can delete own
--   - admin only: can delete others (editor cannot delete others)
--
-- Visibility: follows story visibility (Hidden rules)
-- Story hard-delete: CASCADE deletes all comments

CREATE TABLE story_comments (
    id UUID PRIMARY KEY,
    story_id UUID NOT NULL,
    author_user_id UUID NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_story_comments_story FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE,
    CONSTRAINT chk_story_comments_body_length CHECK (LENGTH(body) <= 1000)
);

CREATE INDEX idx_story_comments_story_id ON story_comments(story_id);
CREATE INDEX idx_story_comments_author_user_id ON story_comments(author_user_id);
CREATE INDEX idx_story_comments_created_at ON story_comments(story_id, created_at);
