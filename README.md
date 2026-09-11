# Genealogy

Modern family genealogy software (P0).

## Stack

- **Backend:** Go 1.22+ (modular monolith)
- **Frontend:** Vue 3 + TypeScript + Vite (see [PR #2](https://github.com/guijs/genealogy/pull/2))
- **Database:** PostgreSQL 16
- **Architecture:** Modular monolith with clean architecture

## Project Layout

```
.
├── cmd/api/              # API server entrypoint
├── internal/
│   ├── domain/           # Business logic (person, kinship, family, projection)
│   ├── app/              # Application services
│   ├── adapter/
│   │   ├── http/         # HTTP handlers (chi router)
│   │   ├── postgres/     # PostgreSQL repositories
│   │   └── objectstore/  # File/media storage
│   └── platform/         # Infrastructure (config, logging)
├── migrations/           # PostgreSQL migrations (golang-migrate)
├── docker-compose.yml    # PostgreSQL for local dev
├── go.mod
└── README.md
```

**Note:** Go code lives at repo root (NOT under `backend/`). Frontend lives in `web/` via [PR #2](https://github.com/guijs/genealogy/pull/2) (NOT `frontend/` or `apps/web/`).

## Development

### Backend

```bash
# Start PostgreSQL
docker-compose up -d

# Run API server
go run ./cmd/api

# Run tests
go test ./...
```

## Current Status (P0 Scaffold + B1)

### Implemented
- ✅ GET /healthz endpoint
- ✅ B1 IDOR Foundation:
  - RequireFamilyMember middleware (404 for non-members)
  - RequireAuth middleware (X-User-Id header, temporary dev stub)
  - GET /api/v1/families/{familyId}
  - GET /api/v1/families/{familyId}/persons (stub)
  - POST /api/v1/families/{familyId}/relationships
- ✅ Domain kinship graph with invariant validation:
  - Self-loop detection
  - Cycle detection (mutual parent, 3-hop cycles)
  - Parent uniqueness (at most one bio/adoptive father/mother each)
  - Bio + adoptive coexistence allowed
- ✅ Pure domain unit tests for kinship invariants
- ✅ HTTP integration tests for IDOR protection

### Not Yet Implemented
- ❌ B1: Persons CRUD (full implementation)
- ❌ B4: Graph query (`GET /api/v1/families/{familyId}/graph`)
- ❌ Database migrations
- ❌ Real authentication (IdP integration)
- ❌ Frontend (see [PR #2](https://github.com/guijs/genealogy/pull/2))

## Design Decisions

- **NO `person.parent_id` column** - relationships stored in separate kinship graph
- **NO Neo4j** - graph operations done in-memory with PostgreSQL as persistence
- **NO microservices** - modular monolith architecture
- **Family-scoped resources** - all API routes under `/api/v1/families/{familyId}/...`

## Gates

Tests must pass before merge:
```bash
go test ./...
```
