-- V1: Initial schema for genealogy application
-- Tables: families, family_members, persons, relationships, unions

-- Families table
CREATE TABLE families (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Family members table (join table for family membership)
CREATE TABLE family_members (
    family_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'viewer',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (family_id, user_id),
    CONSTRAINT fk_family_members_family FOREIGN KEY (family_id) REFERENCES families(id) ON DELETE CASCADE,
    CONSTRAINT chk_family_members_role CHECK (role IN ('admin', 'editor', 'viewer'))
);

CREATE INDEX idx_family_members_user_id ON family_members(user_id);

-- Persons table (no parent_id, includes hidden flag)
CREATE TABLE persons (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    display_name VARCHAR(511),
    gender VARCHAR(20),
    birth_year INTEGER,
    death_year INTEGER,
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_persons_family FOREIGN KEY (family_id) REFERENCES families(id) ON DELETE CASCADE,
    CONSTRAINT chk_persons_gender CHECK (gender IS NULL OR gender IN ('male', 'female', 'unknown', 'unspecified'))
);

CREATE INDEX idx_persons_family_id ON persons(family_id);

-- Relationships table (parent/child with type and dissolved flag)
CREATE TABLE relationships (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL,
    parent_id UUID NOT NULL,
    child_id UUID NOT NULL,
    subtype VARCHAR(20) NOT NULL DEFAULT 'biological',
    role VARCHAR(20) NOT NULL,
    marriage_id UUID,
    dissolved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_relationships_family FOREIGN KEY (family_id) REFERENCES families(id) ON DELETE CASCADE,
    CONSTRAINT fk_relationships_parent FOREIGN KEY (parent_id) REFERENCES persons(id) ON DELETE CASCADE,
    CONSTRAINT fk_relationships_child FOREIGN KEY (child_id) REFERENCES persons(id) ON DELETE CASCADE,
    CONSTRAINT chk_relationships_subtype CHECK (subtype IN ('biological', 'adoptive')),
    CONSTRAINT chk_relationships_role CHECK (role IN ('father', 'mother', 'parent'))
);

CREATE INDEX idx_relationships_family_id ON relationships(family_id);
CREATE INDEX idx_relationships_parent_id ON relationships(parent_id);
CREATE INDEX idx_relationships_child_id ON relationships(child_id);
CREATE INDEX idx_relationships_marriage_id ON relationships(marriage_id);

-- Unions table (partners with status, started/ended dates)
CREATE TABLE unions (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL,
    partner_a_id UUID NOT NULL,
    partner_b_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    started_at VARCHAR(50),
    ended_at VARCHAR(50),
    ended_reason VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_unions_family FOREIGN KEY (family_id) REFERENCES families(id) ON DELETE CASCADE,
    CONSTRAINT fk_unions_partner_a FOREIGN KEY (partner_a_id) REFERENCES persons(id) ON DELETE CASCADE,
    CONSTRAINT fk_unions_partner_b FOREIGN KEY (partner_b_id) REFERENCES persons(id) ON DELETE CASCADE,
    CONSTRAINT chk_unions_status CHECK (status IN ('active', 'divorced', 'widowed', 'ended'))
);

CREATE INDEX idx_unions_family_id ON unions(family_id);
CREATE INDEX idx_unions_partner_a_id ON unions(partner_a_id);
CREATE INDEX idx_unions_partner_b_id ON unions(partner_b_id);

-- Add foreign key from relationships to unions after unions table is created
ALTER TABLE relationships ADD CONSTRAINT fk_relationships_marriage FOREIGN KEY (marriage_id) REFERENCES unions(id) ON DELETE SET NULL;
