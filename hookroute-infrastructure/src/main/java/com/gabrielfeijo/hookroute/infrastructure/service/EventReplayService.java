package com.gabrielfeijo.hookroute.infrastructure.service;

import tools.jackson.databind.node.ObjectNode;
import com.gabrielfeijo.hookroute.domain.WebhookEventStatus;
import com.gabrielfeijo.hookroute.infrastructure.config.HookRouteProperties;
import com.gabrielfeijo.hookroute.infrastructure.persistence.OutboxMessageRepository;
import com.gabrielfeijo.hookroute.infrastructure.persistence.WebhookEventRepository;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventReplayService {
    private final WebhookEventRepository events;
    private final OutboxMessageRepository outbox;
    private final IngestionService ingestion;
    private final HookRouteProperties properties;
    private final Clock clock;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    public EventReplayService(WebhookEventRepository events, OutboxMessageRepository outbox, IngestionService ingestion,
                              HookRouteProperties properties, Clock clock, tools.jackson.databind.ObjectMapper objectMapper) {
        this.events = events;
        this.outbox = outbox;
        this.ingestion = ingestion;
        this.properties = properties;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> replay(String rawId) {
        UUID eventId;
        try { eventId = UUID.fromString(rawId); }
        catch (IllegalArgumentException error) { throw new EventNotFoundException(); }
        var event = events.findById(eventId).orElseThrow(EventNotFoundException::new);
        var storedPayload = objectMapper.readTree(event.payload);
        if (!storedPayload.hasNonNull("eventType") || !storedPayload.has("data") || !storedPayload.get("data").isObject()) {
            throw new InvalidStoredPayloadException();
        }
        UUID jobId = UUID.randomUUID();
        event.jobId = jobId.toString();
        event.status = WebhookEventStatus.QUEUED.value();
        event.lastError = null;
        event.updatedAt = clock.instant();
        ((ObjectNode) storedPayload).put("jobId", jobId.toString()).put("receivedAt", clock.instant().toString());
        event.payload = storedPayload.toString();
        events.save(event);
        outbox.save(ingestion.newOutbox(event.id, properties.getQueue().getMainStream(),
                ingestion.envelope(event, jobId, 1), clock.instant()));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("replayed", true);
        response.put("eventId", event.id.toString());
        response.put("jobId", jobId.toString());
        response.put("jobName", "webhook-event");
        response.put("statusCode", 202);
        response.put("message", "Event replay queued for downstream processing.");
        response.put("payload", storedPayload);
        return response;
    }

    public static class EventNotFoundException extends RuntimeException {}
    public static class InvalidStoredPayloadException extends RuntimeException {}
}
