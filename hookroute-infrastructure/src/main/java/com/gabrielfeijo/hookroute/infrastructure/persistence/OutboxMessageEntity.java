package com.gabrielfeijo.hookroute.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_message")
public class OutboxMessageEntity {
    @Id public UUID id;
    @Column(name = "aggregate_id", nullable = false) public UUID aggregateId;
    @Column(name = "stream_name", nullable = false) public String streamName;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false) public String payload;
    @Column(nullable = false) public String status;
    @Column(nullable = false) public int attempts;
    @Column(name = "available_at", nullable = false) public Instant availableAt;
    @Column(name = "published_at") public Instant publishedAt;
    @Column(name = "last_error") public String lastError;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
}
