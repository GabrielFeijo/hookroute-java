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
    public Acquisition begin(DeliveryJob job) {
        WebhookEventEntity event = findEventForUpdate(job.eventId());
        if (!isCurrentJob(event, job)) return Acquisition.ignored();

        var now = clock.instant();
        int acquired = attempts.acquire(UUID.randomUUID(), event.id, job.jobId(), job.attempt(), now,
                now.minusMillis(properties.getQueue().getVisibilityTimeoutMs()));
        if (acquired == 0) return acquisitionAfterConflict(job);

        event.status = WebhookEventStatus.PROCESSING.value();
        event.updatedAt = now;
        var work = new WorkItem(event.eventType, objectMapper.readTree(event.payload), event.correlationId);
        return Acquisition.acquired(work);
    }

    @Transactional
    public void delivered(DeliveryJob job, String destination) {
        var attempt = attempts.findByJobIdAndAttemptNumber(job.jobId(), job.attempt()).orElseThrow();
        var event = findEventForUpdate(job.eventId());
        if (!isCurrentJob(event, job)) {
            supersede(attempt, destination, null);
            return;
        }

        attempt.status = "delivered";
        attempt.destination = destination;
        attempt.finishedAt = clock.instant();
        event.status = WebhookEventStatus.DELIVERED.value();
        event.lastError = null;
        event.updatedAt = clock.instant();
    }

    @Transactional
    public void failed(DeliveryJob job, String destination, RuntimeException error) {
        var attempt = attempts.findByJobIdAndAttemptNumber(job.jobId(), job.attempt()).orElseThrow();
        var event = findEventForUpdate(job.eventId());
        if (!isCurrentJob(event, job)) {
            supersede(attempt, destination, error.getMessage());
            return;
        }

        attempt.destination = destination;
        attempt.error = error.getMessage();
        attempt.finishedAt = clock.instant();
        event.lastError = error.getMessage();
        event.updatedAt = clock.instant();

        if (job.attempt() < DeliveryJob.MAX_ATTEMPTS) {
            scheduleRetry(job, attempt, event);
            return;
        }
        deadLetter(job, attempt, event, error);
    }

    private Acquisition acquisitionAfterConflict(DeliveryJob job) {
        var attempt = attempts.findByJobIdAndAttemptNumber(job.jobId(), job.attempt()).orElseThrow();
        return "processing".equals(attempt.status) ? Acquisition.busy() : Acquisition.ignored();
    }

    private void scheduleRetry(DeliveryJob job, DeliveryAttemptEntity attempt, WebhookEventEntity event) {
        attempt.status = "failed";
        event.status = WebhookEventStatus.FAILED.value();
        ObjectNode envelope = ingestion.envelope(event, job.jobId(), job.attempt() + 1);
        outbox.save(ingestion.newOutbox(event.id, properties.getQueue().getMainStream(), envelope,
                clock.instant().plusMillis(job.nextBackoffMillis())));
    }

    private void deadLetter(DeliveryJob job, DeliveryAttemptEntity attempt, WebhookEventEntity event,
                            RuntimeException error) {
        attempt.status = "dead_lettered";
        event.status = WebhookEventStatus.DEAD_LETTERED.value();
        ObjectNode envelope = ingestion.envelope(event, job.jobId(), job.attempt());
        envelope.put("failedReason", error.getMessage());
        envelope.put("failedAt", clock.instant().toString());
        outbox.save(ingestion.newOutbox(event.id, properties.getQueue().getDeadLetterStream(), envelope, clock.instant()));
    }

    private WebhookEventEntity findEventForUpdate(UUID eventId) {
        return events.findByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
    }

    private boolean isCurrentJob(WebhookEventEntity event, DeliveryJob job) {
        return job.jobId().toString().equals(event.jobId);
    }

    private void supersede(DeliveryAttemptEntity attempt, String destination, String error) {
        attempt.status = "superseded";
        attempt.destination = destination;
        attempt.error = error;
        attempt.finishedAt = clock.instant();
    }

    public record WorkItem(String eventType, tools.jackson.databind.JsonNode payload, String correlationId) {}

    public record Acquisition(WorkItem workItem, boolean retryLater) {
        static Acquisition acquired(WorkItem workItem) { return new Acquisition(workItem, false); }
        static Acquisition ignored() { return new Acquisition(null, false); }
        static Acquisition busy() { return new Acquisition(null, true); }
    }
}
