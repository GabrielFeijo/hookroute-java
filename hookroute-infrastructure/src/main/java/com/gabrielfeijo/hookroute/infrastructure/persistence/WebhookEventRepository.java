package com.gabrielfeijo.hookroute.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookEventRepository extends JpaRepository<WebhookEventEntity, UUID>, JpaSpecificationExecutor<WebhookEventEntity> {
    Optional<WebhookEventEntity> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from WebhookEventEntity event where event.id = :id")
    Optional<WebhookEventEntity> findByIdForUpdate(@Param("id") UUID id);

    long countByStatus(String status);
}
