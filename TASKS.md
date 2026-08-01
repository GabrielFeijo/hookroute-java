# HookRoute Java — implementation backlog

- [x] TASK-00 Repository and Maven multi-module scaffold
- [x] TASK-01 Domain and application boundaries
- [x] TASK-02 Typed application configuration
- [x] TASK-03 PostgreSQL schema, Flyway, JPA and concurrent idempotency
- [x] TASK-04 Transactional outbox and retrying publisher
- [x] TASK-05 Compatible ingestion HTTP endpoint
- [x] TASK-06 Raw-body HMAC verification
- [x] TASK-07 Redis Streams consumer group
- [x] TASK-08 Worker retries and dead-letter stream
- [x] TASK-09 Simulated/real egress and per-destination circuit breaker
- [x] TASK-10 Event queries and replay
- [x] TASK-11 Health, compatible metrics, Actuator, Prometheus and correlation ID
- [x] TASK-12 OpenAPI and documentation
- [x] TASK-13 Non-root Docker images and local Compose
- [x] TASK-14 Unit and contract-focused tests
- [x] TASK-15 CI and dependency update automation
- [x] TASK-16 Contract parity documentation

Future production hardening should add distributed tracing export, authenticated operational endpoints, Redis pending
message auto-claim tuning under load, and sustained performance tests in an environment representative of production.
