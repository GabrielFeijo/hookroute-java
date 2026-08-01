# AGENTS.md

## Project overview

HookRoute Java is a Spring Boot webhook engine. Keep ingestion thin, persist event and outbox atomically, and perform
all external delivery in the worker.

## Boundaries

- Domain must not depend on Spring, JPA, Redis, or HTTP.
- API must not dispatch webhooks directly.
- Worker processing is at-least-once and must remain idempotent by job ID and attempt number.
- Database changes require a versioned Flyway migration.
- Public API changes require DTO, OpenAPI, parity documentation, and tests.

## Commands

- Build and test: `./mvnw clean verify`
- Start infrastructure: `docker compose up -d postgres redis`
- Start API: `./mvnw -pl hookroute-api -am spring-boot:run`
- Start worker: `./mvnw -pl hookroute-worker -am spring-boot:run`
