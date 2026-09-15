-- V3: Add progenitor_person_id to families table for lineage projection
-- One progenitor per family; nullable (no auto-default).
-- FK with ON DELETE SET NULL so deleting the person clears the progenitor.

ALTER TABLE families ADD COLUMN progenitor_person_id UUID NULL;

ALTER TABLE families ADD CONSTRAINT fk_families_progenitor
    FOREIGN KEY (progenitor_person_id) REFERENCES persons(id) ON DELETE SET NULL;
