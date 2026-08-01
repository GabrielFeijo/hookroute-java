package com.gabrielfeijo.hookroute.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "webhook_event")
public class WebhookEventEntity {
    @Id public UUID id;
    @Column(name = "event_type", nullable = false) public String eventType;
    @Column(nullable = false) public String provider;
    @Column(name = "job_id") public String jobId;
    @Column(name = "idempotency_key", unique = true) public String idempotencyKey;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false) public String payload;
    @Column(nullable = false) public String status;
    @Column(name = "last_error") public String lastError;
    @Column(name = "correlation_id") public String correlationId;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @Column(name = "updated_at", nullable = false) public Instant updatedAt;
    @Version public long version;
}
