# Database Migrations

This directory will contain PostgreSQL migrations managed by golang-migrate.

## Planned schema (NOT YET IMPLEMENTED)

- `families` - family tree containers
- `persons` - individuals in family trees
- `kinship_edges` - parent-child relationships (NO person.parent_id column)
- `unions` - marriages/partnerships

Migrations will be added in follow-up slices (B1: Persons, B3: Relationships).
