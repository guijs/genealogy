-- V4: Add generation names (字辈) sequence to families
-- generation_names: ordered array of generation name characters/strings
-- generation_name_align: alignment mode 'A' (default, index k -> gen k+1) or 'B' (index k -> gen k)

ALTER TABLE families ADD COLUMN generation_names VARCHAR(16) ARRAY DEFAULT NULL;

ALTER TABLE families ADD COLUMN generation_name_align CHAR(1) NOT NULL DEFAULT 'A'
    CHECK (generation_name_align IN ('A', 'B'));
