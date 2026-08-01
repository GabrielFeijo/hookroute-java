package com.gabrielfeijo.hookroute.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttemptEntity, UUID> {
    Optional<DeliveryAttemptEntity> findByJobIdAndAttemptNumber(UUID jobId, int attemptNumber);
}
