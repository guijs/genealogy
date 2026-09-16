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

## Current Migrations

| Version | Description |
|---------|-------------|
| V1 | Initial schema (families, family_members, persons, relationships, unions) |
| V2 | Users table for JWT authentication |
| V3 | Progenitor support |
| V4 | Generation names |
| V5 | Stories feature |

Migrations are automatically applied on Spring Boot startup via `flyway.baseline-on-migrate: true`.
