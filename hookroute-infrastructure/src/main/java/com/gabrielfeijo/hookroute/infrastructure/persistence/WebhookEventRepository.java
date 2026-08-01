package com.gabrielfeijo.hookroute.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WebhookEventRepository extends JpaRepository<WebhookEventEntity, UUID>, JpaSpecificationExecutor<WebhookEventEntity> {
    Optional<WebhookEventEntity> findByIdempotencyKey(String idempotencyKey);
    long countByStatus(String status);
}
