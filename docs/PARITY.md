# NestJS parity

## Preserved

- Public endpoints, HTTP status codes, response fields and lifecycle values.
- The ingestion OpenAPI contract exposes the typed `eventId`, `eventType` and `data` fields and rejects unknown fields.
- Optional HMAC based on the exact raw body and timestamp tolerance.
- Idempotency through producer `eventId`.
- Five delivery attempts, exponential backoff, DLQ, replay, simulated/real egress and circuit breaker.
- PostgreSQL audit, Redis, health, metrics and Swagger at `/docs`.

## Intentional improvements

- PostgreSQL transactional outbox replaces the non-atomic database-then-BullMQ publication sequence.
- Redis Streams replaces BullMQ; wire-level BullMQ interoperability is not supported.
- API acceptance remains available during temporary Redis outages after the PostgreSQL commit.
- Job IDs are pre-generated UUID strings.
- Attempts and replays have durable history in `event_delivery_attempt`.
- Correlation ID is persisted and propagated to worker and destination.
- Real egress sends `Idempotency-Key`; delivery remains at-least-once.
- Worker attempts use a database-backed lease; duplicate and superseded jobs are acknowledged without dispatch.
- Actuator exposes production-oriented readiness, liveness and Prometheus metrics.

The event query payload intentionally excludes the internal attempt and outbox tables to keep the original API contract.
