package com.gabrielfeijo.hookroute.infrastructure.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.gabrielfeijo.hookroute.infrastructure.persistence.WebhookEventEntity;
import com.gabrielfeijo.hookroute.infrastructure.persistence.WebhookEventRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventQueryService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private final WebhookEventRepository events;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    public EventQueryService(WebhookEventRepository events, ObjectMapper objectMapper, EntityManager entityManager) {
        this.events = events;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(String status, String eventType, String jobId, int limit, int offset, boolean failuresOnly) {
        var cb = entityManager.getCriteriaBuilder();
        var itemQuery = cb.createQuery(WebhookEventEntity.class);
        var itemRoot = itemQuery.from(WebhookEventEntity.class);
        itemQuery.where(predicates(cb, itemRoot, status, eventType, jobId, failuresOnly));
        itemQuery.orderBy(cb.desc(itemRoot.get("createdAt")));
        var items = entityManager.createQuery(itemQuery).setFirstResult(offset).setMaxResults(limit)
                .getResultList().stream().map(this::toResponse).toList();

        var countQuery = cb.createQuery(Long.class);
        var countRoot = countQuery.from(WebhookEventEntity.class);
        countQuery.select(cb.count(countRoot));
        countQuery.where(predicates(cb, countRoot, status, eventType, jobId, failuresOnly));
        long total = entityManager.createQuery(countQuery).getSingleResult();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("limit", limit);
        result.put("offset", offset);
        result.put("items", items);
        return result;
    }

    private Predicate[] predicates(jakarta.persistence.criteria.CriteriaBuilder cb,
                                   jakarta.persistence.criteria.Root<WebhookEventEntity> root,
                                   String status, String eventType, String jobId, boolean failuresOnly) {
        var predicates = new ArrayList<Predicate>();
        if (failuresOnly) predicates.add(root.get("status").in("failed", "dead_lettered"));
        else if (status != null) predicates.add(cb.equal(root.get("status"), status));
        if (eventType != null) predicates.add(cb.equal(root.get("eventType"), eventType));
        if (jobId != null) predicates.add(cb.equal(root.get("jobId"), jobId));
        return predicates.toArray(Predicate[]::new);
    }

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> find(String id) {
        try {
            return events.findById(UUID.fromString(id)).map(this::toResponse);
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public Map<String, Object> toResponse(WebhookEventEntity event) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", event.id.toString());
        response.put("eventType", event.eventType);
        response.put("provider", event.provider);
        response.put("jobId", event.jobId);
        response.put("idempotencyKey", event.idempotencyKey);
        response.put("payload", objectMapper.convertValue(objectMapper.readTree(event.payload), MAP_TYPE));
        response.put("status", event.status);
        response.put("lastError", event.lastError);
        response.put("createdAt", event.createdAt);
        response.put("updatedAt", event.updatedAt);
        return response;
    }
}
