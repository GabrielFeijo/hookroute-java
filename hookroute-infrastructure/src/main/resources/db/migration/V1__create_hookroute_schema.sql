CREATE TABLE webhook_event (
    id UUID PRIMARY KEY,
    event_type VARCHAR(255) NOT NULL,
    provider VARCHAR(255) NOT NULL,
    job_id VARCHAR(255),
    idempotency_key VARCHAR(255) UNIQUE,
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL,
    last_error TEXT,
    correlation_id VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_webhook_event_status ON webhook_event(status);
CREATE INDEX idx_webhook_event_type ON webhook_event(event_type);
CREATE INDEX idx_webhook_event_job_id ON webhook_event(job_id);

CREATE TABLE event_delivery_attempt (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES webhook_event(id),
    job_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    destination TEXT,
    error TEXT,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    UNIQUE(job_id, attempt_number)
);

CREATE INDEX idx_delivery_attempt_event ON event_delivery_attempt(event_id);

CREATE TABLE outbox_message (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    stream_name VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_outbox_pending ON outbox_message(status, available_at);
