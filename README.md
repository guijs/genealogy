# Genealogy

Modern family genealogy software.

## Stack

- **Backend:** Spring Boot 3.x (JDK 17) — modular monolith under `server/`
- **ORM:** MyBatis (no JPA/Hibernate)
- **Migrations:** Flyway (no Liquibase)
- **Database:** PostgreSQL 16
- **Frontend:** Vue 3 + TypeScript + Vite under `web/`

## Project Layout

```
.
├── server/               # Active Java backend (Spring Boot)
│   ├── src/main/java/    # Application code
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/ # Flyway migrations
│   └── pom.xml           # Maven build (strict dependency allowlist)
├── web/                  # Vue 3 frontend (unchanged)
├── legacy/go/            # Archived Go backend (read-only reference)
├── docker-compose.yml    # PostgreSQL for local dev
└── README.md
```

## Development

### Prerequisites

- JDK 17+
- Maven 3.8+
- Docker & Docker Compose (for PostgreSQL)

### Backend

```bash
# Start PostgreSQL
docker-compose up -d

# Build and run
cd server
./mvnw spring-boot:run

# Or build JAR
./mvnw clean package
java -jar target/genealogy-server-0.0.1-SNAPSHOT.jar

# Run tests (no live PG required)
./mvnw test
```

### Health Check

```bash
curl http://localhost:8080/healthz
# {"status":"ok"}
```

### Frontend

See [web/README.md](web/README.md) for frontend development instructions.

### Local Development Guide

For a complete walkthrough on running the demo locally, see **[docs/LOCAL.md](docs/LOCAL.md)** — covers prerequisites, database setup, environment variables, and the register/login flow.

## Current Status

### Implemented
- ✅ Spring Boot scaffold with strict dependency allowlist
- ✅ `GET /healthz` endpoint
- ✅ Flyway migrations with schema (persons, families, relationships, unions, users, stories)
- ✅ MyBatis data access layer
- ✅ JWT authentication (`POST /api/v1/auth/register`, `POST /api/v1/auth/login`)
- ✅ Person/Family/Relationship/Union CRUD
- ✅ Family graph query API
- ✅ Local media upload
- ✅ Family stories feature
- ✅ Vue 3 frontend with mock data support

### Not Yet Implemented
- ❌ SSO / OAuth third-party login
- ❌ Pedigree print/export (PDF/image)
- ❌ Historical point-in-time view (asOf)
- ❌ Real-time collaborative editing

## Design Decisions

- **Java backend only** — Go code archived in `legacy/go/` for reference
- **MyBatis over JPA** — explicit SQL, no ORM magic
- **Flyway only** — simple versioned migrations
- **No distributed stack** — no Spring Cloud, Gateway, Config Server, message brokers
- **Modular monolith** — clean architecture without microservices complexity

## Dependency Policy

See [server/DEPENDENCY_ALLOWLIST.md](server/DEPENDENCY_ALLOWLIST.md) for the strict
dependency allowlist and denial rules.

## Gates

Tests must pass before merge:
```bash
cd server && ./mvnw test
```
