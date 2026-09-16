-- V8: Comment Mentions feature (P1 MVP v0.2)
--
-- Allows @mentioning current family member users in comments.
-- Anchored on user_id (login User account), NOT person_id.
--
-- Rules:
--   - Write: validate user_id is current family member → else 400
--   - Read: include mentions with visibility flag for left members
--   - Historical: keep snapshot when user leaves family (ON DELETE SET NULL)
--   - No auto-parse from body text; only submitted mentions list
--   - No notifications (this knife)

CREATE TABLE comment_mentions (
    id UUID PRIMARY KEY,
    comment_id UUID NOT NULL,
    user_id UUID,
    display_name_snapshot VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_comment_mentions_comment FOREIGN KEY (comment_id) REFERENCES story_comments(id) ON DELETE CASCADE
);

CREATE INDEX idx_comment_mentions_comment_id ON comment_mentions(comment_id);
CREATE INDEX idx_comment_mentions_user_id ON comment_mentions(user_id);
