# Dependency Allowlist

This document defines the strict dependency allowlist for the genealogy-server project.

## ALLOWED Dependencies

| Dependency | Purpose |
|------------|---------|
| `spring-boot-starter-web` | REST API, embedded Tomcat |
| `spring-boot-starter-validation` | Bean validation (JSR-380) |
| `spring-boot-starter-test` | Testing (JUnit 5, Mockito, etc.) |
| `mybatis-spring-boot-starter` | MyBatis ORM integration |
| `postgresql` | PostgreSQL JDBC driver |
| `flyway-core` | Database migrations |
| `flyway-database-postgresql` | Flyway PostgreSQL dialect (Boot 3.x) |
| Boot default logging | Logback via spring-boot-starter |
| `h2` (test scope only) | In-memory DB for unit tests |

## DENIED Dependencies

The following are **explicitly forbidden** in this project:

### ORM / Data Access
- ❌ `spring-boot-starter-data-jpa` — Use MyBatis only
- ❌ `hibernate-*` — No Hibernate
- ❌ `spring-data-*` — No Spring Data

### Migration
- ❌ `liquibase-*` — Use Flyway only

### Enterprise / Distributed Stack
- ❌ `spring-cloud-*` — No Spring Cloud
- ❌ `spring-cloud-starter-gateway` — No API Gateway
- ❌ `spring-cloud-config-*` — No Config Server
- ❌ `spring-cloud-starter-netflix-eureka-*` — No Eureka
- ❌ `spring-cloud-starter-alibaba-nacos-*` — No Nacos
- ❌ `spring-cloud-starter-openfeign` — No OpenFeign
- ❌ `spring-kafka` / `spring-rabbit` — No message brokers
- ❌ `spring-data-elasticsearch` — No Elasticsearch
- ❌ `seata-*` / `atomikos-*` — No distributed TX

## Rationale

This project follows a **modular monolith** architecture. We intentionally keep the
dependency footprint minimal to reduce complexity, improve startup time, and maintain
a simple deployment model.

Any new dependency must be reviewed and added to this allowlist before inclusion.
