package com.gabrielfeijo.hookroute.infrastructure.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.gabrielfeijo.hookroute.application.IngestionCommand;
import com.gabrielfeijo.hookroute.application.IngestionResult;
import com.gabrielfeijo.hookroute.application.port.IngestionPort;
import com.gabrielfeijo.hookroute.domain.WebhookEventStatus;
import com.gabrielfeijo.hookroute.infrastructure.config.HookRouteProperties;
import com.gabrielfeijo.hookroute.infrastructure.persistence.OutboxMessageEntity;
import com.gabrielfeijo.hookroute.infrastructure.persistence.OutboxMessageRepository;
import com.gabrielfeijo.hookroute.infrastructure.persistence.WebhookEventEntity;
import com.gabrielfeijo.hookroute.infrastructure.persistence.WebhookEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionService implements IngestionPort {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final WebhookEventRepository events;
    private final OutboxMessageRepository outbox;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final HookRouteProperties properties;
    private final Clock clock;

    public IngestionService(WebhookEventRepository events, OutboxMessageRepository outbox, JdbcTemplate jdbc,
                            ObjectMapper objectMapper, HookRouteProperties properties, Clock clock) {
        this.events = events;
        this.outbox = outbox;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public IngestionResult ingest(IngestionCommand command) {
        var input = command.payload();
        if (input.idempotencyKey() != null) {
            jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", (ResultSetExtractor<Void>) resultSet -> null,
                    input.idempotencyKey());
            var existing = events.findByIdempotencyKey(input.idempotencyKey());
            if (existing.isPresent()) return duplicate(existing.get());
        }

        Instant now = clock.instant();
        UUID eventId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("eventType", input.eventType());
        payload.set("data", objectMapper.valueToTree(input.data()));
        payload.put("receivedAt", input.receivedAt().toString());
        payload.put("source", input.source());
        if (input.idempotencyKey() != null) payload.put("idempotencyKey", input.idempotencyKey());
        payload.put("auditEventId", eventId.toString());
        payload.put("jobId", jobId.toString());

        var event = new WebhookEventEntity();
        event.id = eventId;
        event.eventType = input.eventType();
        event.provider = input.source();
        event.jobId = jobId.toString();
        event.idempotencyKey = input.idempotencyKey();
        event.payload = payload.toString();
        event.status = WebhookEventStatus.QUEUED.value();
        event.correlationId = command.correlationId();
        event.createdAt = now;
        event.updatedAt = now;
        events.save(event);

        outbox.save(newOutbox(eventId, properties.getQueue().getMainStream(), envelope(event, jobId, 1), now));
        return new IngestionResult(false, jobId.toString(), eventId.toString(), objectMapper.convertValue(payload, MAP_TYPE));
    }

    private IngestionResult duplicate(WebhookEventEntity event) {
        return new IngestionResult(true, event.jobId, event.id.toString(),
                objectMapper.convertValue(objectMapper.readTree(event.payload), MAP_TYPE));
    }

    ObjectNode envelope(WebhookEventEntity event, UUID jobId, int attempt) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("jobName", "webhook-event");
        node.put("jobId", jobId.toString());
        node.put("eventId", event.id.toString());
        node.put("attempt", attempt);
        node.put("correlationId", event.correlationId);
        node.set("payload", objectMapper.readTree(event.payload));
        return node;
    }

    OutboxMessageEntity newOutbox(UUID eventId, String stream, ObjectNode payload, Instant availableAt) {
        var message = new OutboxMessageEntity();
        message.id = UUID.randomUUID();
        message.aggregateId = eventId;
        message.streamName = stream;
        message.payload = payload.toString();
        message.status = "PENDING";
        message.attempts = 0;
        message.availableAt = availableAt;
        message.createdAt = clock.instant();
        return message;
    }
}
