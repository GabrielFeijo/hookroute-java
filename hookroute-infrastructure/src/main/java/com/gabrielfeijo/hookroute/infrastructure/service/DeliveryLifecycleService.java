package com.gabrielfeijo.hookroute.infrastructure.service;

import tools.jackson.databind.node.ObjectNode;
import com.gabrielfeijo.hookroute.domain.DeliveryJob;
import com.gabrielfeijo.hookroute.domain.WebhookEventStatus;
import com.gabrielfeijo.hookroute.infrastructure.config.HookRouteProperties;
import com.gabrielfeijo.hookroute.infrastructure.persistence.DeliveryAttemptEntity;
import com.gabrielfeijo.hookroute.infrastructure.persistence.DeliveryAttemptRepository;
import com.gabrielfeijo.hookroute.infrastructure.persistence.OutboxMessageRepository;
import com.gabrielfeijo.hookroute.infrastructure.persistence.WebhookEventEntity;
import com.gabrielfeijo.hookroute.infrastructure.persistence.WebhookEventRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryLifecycleService {
    private final WebhookEventRepository events;
    private final DeliveryAttemptRepository attempts;
    private final OutboxMessageRepository outbox;
    private final IngestionService ingestion;
    private final HookRouteProperties properties;
    private final Clock clock;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    public DeliveryLifecycleService(WebhookEventRepository events, DeliveryAttemptRepository attempts,
                                    OutboxMessageRepository outbox, IngestionService ingestion,
                                    HookRouteProperties properties, Clock clock, tools.jackson.databind.ObjectMapper objectMapper) {
        this.events = events;
        this.attempts = attempts;
        this.outbox = outbox;
        this.ingestion = ingestion;
        this.properties = properties;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WorkItem begin(DeliveryJob job) {
        var previous = attempts.findByJobIdAndAttemptNumber(job.jobId(), job.attempt());
        if (previous.isPresent() && !previous.get().status.equals("processing")) return null;
        WebhookEventEntity event = events.findById(job.eventId()).orElseThrow(() -> new IllegalArgumentException("Event not found"));
        if (previous.isEmpty()) {
            var attempt = new DeliveryAttemptEntity();
            attempt.id = UUID.randomUUID();
            attempt.eventId = event.id;
            attempt.jobId = job.jobId();
            attempt.attemptNumber = job.attempt();
            attempt.status = "processing";
            attempt.startedAt = clock.instant();
            attempts.save(attempt);
        }
        event.status = WebhookEventStatus.PROCESSING.value();
        event.updatedAt = clock.instant();
        events.save(event);
        return new WorkItem(event.eventType, objectMapper.readTree(event.payload), event.correlationId);
    }

    @Transactional
    public void delivered(DeliveryJob job, String destination) {
        var attempt = attempts.findByJobIdAndAttemptNumber(job.jobId(), job.attempt()).orElseThrow();
        attempt.status = "delivered";
        attempt.destination = destination;
        attempt.finishedAt = clock.instant();
        var event = events.findById(job.eventId()).orElseThrow();
        event.status = WebhookEventStatus.DELIVERED.value();
        event.lastError = null;
        event.updatedAt = clock.instant();
    }

    @Transactional
    public void failed(DeliveryJob job, String destination, RuntimeException error) {
        var attempt = attempts.findByJobIdAndAttemptNumber(job.jobId(), job.attempt()).orElseThrow();
        attempt.destination = destination;
        attempt.error = error.getMessage();
        attempt.finishedAt = clock.instant();
        var event = events.findById(job.eventId()).orElseThrow();
        event.lastError = error.getMessage();
        event.updatedAt = clock.instant();

        if (job.attempt() < DeliveryJob.MAX_ATTEMPTS) {
            attempt.status = "failed";
            event.status = WebhookEventStatus.FAILED.value();
            ObjectNode envelope = ingestion.envelope(event, job.jobId(), job.attempt() + 1);
            outbox.save(ingestion.newOutbox(event.id, properties.getQueue().getMainStream(), envelope,
                    clock.instant().plusMillis(job.nextBackoffMillis())));
        } else {
            attempt.status = "dead_lettered";
            event.status = WebhookEventStatus.DEAD_LETTERED.value();
            ObjectNode envelope = ingestion.envelope(event, job.jobId(), job.attempt());
            envelope.put("failedReason", error.getMessage());
            envelope.put("failedAt", clock.instant().toString());
            outbox.save(ingestion.newOutbox(event.id, properties.getQueue().getDeadLetterStream(), envelope, clock.instant()));
        }
    }

    public record WorkItem(String eventType, tools.jackson.databind.JsonNode payload, String correlationId) {}
}
