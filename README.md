# Genealogy

Modern family genealogy software (P0).

## Stack

- **Backend:** Go 1.22+ (modular monolith)
- **Frontend:** Vue 3 + TypeScript + Vite
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
├── web/                  # Vue 3 + TypeScript frontend
├── docker-compose.yml    # PostgreSQL for local dev
├── go.mod
└── README.md
```

**Note:** Go code lives at repo root (NOT under `backend/`). Frontend lives in `web/` (NOT `frontend/` or `apps/web/`).

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

### Frontend

```bash
cd web
npm install
npm run dev
```

## Current Status (P0 Scaffold)

### Implemented
- ✅ GET /healthz endpoint
- ✅ Domain kinship graph with invariant validation:
  - Self-loop detection
  - Cycle detection (mutual parent, 3-hop cycles)
  - Biological parent uniqueness (at most one bio father/mother)
- ✅ Pure domain unit tests for kinship invariants

### Not Yet Implemented
- ❌ B1: Persons CRUD (`POST/GET /api/v1/families/{familyId}/persons`)
- ❌ B3: Relationships (`POST /api/v1/families/{familyId}/relationships`)
- ❌ B4: Graph query (`GET /api/v1/families/{familyId}/graph`)
- ❌ Database migrations
- ❌ Authentication & RequireFamilyMember middleware

## Design Decisions

- **NO `person.parent_id` column** - relationships stored in separate kinship_edges table
- **NO Neo4j** - graph operations done in-memory with PostgreSQL as persistence
- **NO microservices** - modular monolith architecture
- **Family-scoped resources** - all API routes will be under `/api/v1/families/{familyId}/...`

## Gates

Tests must pass before merge:
```bash
go test ./...
cd web && npm run build
```
