# Legacy Go Backend (Archived)

**Status:** ⚠️ ARCHIVED — Read-only reference code

This directory contains the original Go backend implementation. It has been archived
as of Phase 0 migration to Java/Spring Boot.

## Active Backend

The active backend is now located at `server/` (Spring Boot + JDK 17 + MyBatis).

**Do not modify or extend this code.** It is preserved for historical reference only.

## Original Structure

```
legacy/go/
├── cmd/api/              # Original Go API entrypoint
├── internal/
│   ├── domain/           # Business logic (person, kinship, family)
│   ├── app/              # Application services
│   ├── adapter/          # Adapters (HTTP, Postgres, ObjectStore)
│   └── platform/         # Infrastructure
├── migrations/           # Original golang-migrate migrations
├── go.mod
└── go.sum
```

## Migration Notes

- The Go codebase implemented IDOR protection, kinship graph, and relationship services
- Domain logic and API contracts will be ported to Java in Phase 1+
- This code serves as specification reference for the new implementation
