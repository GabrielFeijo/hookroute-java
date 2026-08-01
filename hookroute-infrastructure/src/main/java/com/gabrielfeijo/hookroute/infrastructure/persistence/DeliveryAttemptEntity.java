package com.gabrielfeijo.hookroute.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_delivery_attempt")
public class DeliveryAttemptEntity {
    @Id public UUID id;
    @Column(name = "event_id", nullable = false) public UUID eventId;
    @Column(name = "job_id", nullable = false) public UUID jobId;
    @Column(name = "attempt_number", nullable = false) public int attemptNumber;
    @Column(nullable = false) public String status;
    public String destination;
    public String error;
    @Column(name = "started_at", nullable = false) public Instant startedAt;
    @Column(name = "finished_at") public Instant finishedAt;
}
