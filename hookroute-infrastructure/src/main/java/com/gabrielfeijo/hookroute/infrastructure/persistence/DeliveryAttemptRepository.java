package com.gabrielfeijo.hookroute.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttemptEntity, UUID> {
    Optional<DeliveryAttemptEntity> findByJobIdAndAttemptNumber(UUID jobId, int attemptNumber);

    @Modifying
    @Query(value = """
            INSERT INTO event_delivery_attempt
                (id, event_id, job_id, attempt_number, status, started_at)
            VALUES
                (:id, :eventId, :jobId, :attemptNumber, 'processing', :startedAt)
            ON CONFLICT (job_id, attempt_number) DO UPDATE
            SET status = 'processing', started_at = EXCLUDED.started_at,
                finished_at = NULL, destination = NULL, error = NULL
            WHERE event_delivery_attempt.status = 'processing'
              AND event_delivery_attempt.started_at <= :leaseExpiredBefore
            """, nativeQuery = true)
    int acquire(@Param("id") UUID id,
                @Param("eventId") UUID eventId,
                @Param("jobId") UUID jobId,
                @Param("attemptNumber") int attemptNumber,
                @Param("startedAt") Instant startedAt,
                @Param("leaseExpiredBefore") Instant leaseExpiredBefore);
}
