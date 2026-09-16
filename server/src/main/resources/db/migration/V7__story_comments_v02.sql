-- V7: Migrate Story Comments to v0.2 frozen spec
--
-- Changes from V6 (① schedule) to v0.2:
--   - Remove parent_comment_id (flat comments only, no replies)
--   - Add updated_at column for conditional write (409 on conflict)
--
-- v0.2 access rules:
--   - viewer: read-only (cannot create/edit/delete)
--   - admin/editor: can create comments
--   - author: can edit own (with conditional write 409), can delete own
--   - admin only: can delete others (editor cannot)

-- Drop the parent_comment_id index first
DROP INDEX IF EXISTS idx_story_comments_parent_id;

-- Drop the parent FK constraint
ALTER TABLE story_comments DROP CONSTRAINT IF EXISTS fk_story_comments_parent;

-- Drop the parent_comment_id column
ALTER TABLE story_comments DROP COLUMN IF EXISTS parent_comment_id;

-- Add updated_at column for conditional write support
ALTER TABLE story_comments ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Update existing rows to have updated_at = created_at if null
UPDATE story_comments SET updated_at = created_at WHERE updated_at IS NULL;
