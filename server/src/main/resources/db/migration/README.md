# Flyway Migrations

This directory contains PostgreSQL database migration scripts managed by Flyway.

## Naming Convention

Migration files must follow Flyway's naming convention:

```
V{version}__{description}.sql
```

Examples:
- `V1__create_person_table.sql`
- `V2__add_relationship_table.sql`
- `V3__add_family_table.sql`

## Status

**Phase 0**: No business schema migrations yet. This is a placeholder directory.

Business domain migrations will be added in Phase 1 when CRUD operations are implemented.
