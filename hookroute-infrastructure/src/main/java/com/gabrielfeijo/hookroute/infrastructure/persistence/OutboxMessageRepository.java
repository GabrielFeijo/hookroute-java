package com.gabrielfeijo.hookroute.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import jakarta.persistence.QueryHint;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessageEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select o from OutboxMessageEntity o where o.status = 'PENDING' and o.availableAt <= :now order by o.createdAt")
    List<OutboxMessageEntity> lockDue(Instant now, Pageable pageable);

    long countByStatus(String status);
}
