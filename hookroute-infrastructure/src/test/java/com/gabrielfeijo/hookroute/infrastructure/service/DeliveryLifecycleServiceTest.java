package com.gabrielfeijo.hookroute.infrastructure.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gabrielfeijo.hookroute.domain.DeliveryJob;
import com.gabrielfeijo.hookroute.infrastructure.config.HookRouteProperties;
import com.gabrielfeijo.hookroute.infrastructure.persistence.DeliveryAttemptEntity;
import com.gabrielfeijo.hookroute.infrastructure.persistence.DeliveryAttemptRepository;
import com.gabrielfeijo.hookroute.infrastructure.persistence.OutboxMessageRepository;
import com.gabrielfeijo.hookroute.infrastructure.persistence.WebhookEventEntity;
import com.gabrielfeijo.hookroute.infrastructure.persistence.WebhookEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class DeliveryLifecycleServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

    private final WebhookEventRepository events = mock(WebhookEventRepository.class);
    private final DeliveryAttemptRepository attempts = mock(DeliveryAttemptRepository.class);
    private final OutboxMessageRepository outbox = mock(OutboxMessageRepository.class);
    private final IngestionService ingestion = mock(IngestionService.class);
    private final HookRouteProperties properties = new HookRouteProperties();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private DeliveryLifecycleService lifecycle;

    @BeforeEach
    void setUp() {
        lifecycle = new DeliveryLifecycleService(events, attempts, outbox, ingestion, properties,
                Clock.fixed(NOW, ZoneOffset.UTC), objectMapper);
    }

    @Test
    void ignoresJobSupersededByReplay() {
        var job = job(1);
        var event = event(job.eventId(), UUID.randomUUID());
        when(events.findByIdForUpdate(job.eventId())).thenReturn(Optional.of(event));

        var acquisition = lifecycle.begin(job);

        assertNull(acquisition.workItem());
        assertFalse(acquisition.retryLater());
        verify(attempts, never()).acquire(any(), any(), any(), any(Integer.class), any(), any());
    }

    @Test
    void keepsConcurrentDuplicatePendingWhileAttemptLeaseIsActive() {
        var job = job(1);
        var event = event(job.eventId(), job.jobId());
        var attempt = attempt(job, "processing");
        when(events.findByIdForUpdate(job.eventId())).thenReturn(Optional.of(event));
        when(attempts.acquire(any(), any(), any(), any(Integer.class), any(), any())).thenReturn(0);
        when(attempts.findByJobIdAndAttemptNumber(job.jobId(), job.attempt())).thenReturn(Optional.of(attempt));

        var acquisition = lifecycle.begin(job);

        assertNull(acquisition.workItem());
        assertTrue(acquisition.retryLater());
    }

    @Test
    void ignoresDuplicateOfCompletedAttempt() {
        var job = job(1);
        var event = event(job.eventId(), job.jobId());
        when(events.findByIdForUpdate(job.eventId())).thenReturn(Optional.of(event));
        when(attempts.acquire(any(), any(), any(), any(Integer.class), any(), any())).thenReturn(0);
        when(attempts.findByJobIdAndAttemptNumber(job.jobId(), job.attempt()))
                .thenReturn(Optional.of(attempt(job, "delivered")));

        var acquisition = lifecycle.begin(job);

        assertNull(acquisition.workItem());
        assertFalse(acquisition.retryLater());
    }

    @Test
    void acquiresAttemptAndReturnsWorkOnlyOnce() {
        var job = job(1);
        var event = event(job.eventId(), job.jobId());
        when(events.findByIdForUpdate(job.eventId())).thenReturn(Optional.of(event));
        when(attempts.acquire(any(), any(), any(), any(Integer.class), any(), any())).thenReturn(1);

        var acquisition = lifecycle.begin(job);

        assertEquals("order.created", acquisition.workItem().eventType());
        assertFalse(acquisition.retryLater());
        assertEquals("processing", event.status);
    }

    @Test
    void completionOfSupersededJobDoesNotOverwriteReplayState() {
        var job = job(1);
        var attempt = attempt(job, "processing");
        var event = event(job.eventId(), UUID.randomUUID());
        event.status = "queued";
        when(attempts.findByJobIdAndAttemptNumber(job.jobId(), job.attempt())).thenReturn(Optional.of(attempt));
        when(events.findByIdForUpdate(job.eventId())).thenReturn(Optional.of(event));

        lifecycle.delivered(job, "https://example.test/order.created");

        assertEquals("superseded", attempt.status);
        assertEquals("queued", event.status);
        verify(outbox, never()).save(any());
    }

    private DeliveryJob job(int number) {
        return new DeliveryJob(UUID.randomUUID(), UUID.randomUUID(), number, "correlation");
    }

    private WebhookEventEntity event(UUID eventId, UUID jobId) {
        var event = new WebhookEventEntity();
        event.id = eventId;
        event.jobId = jobId.toString();
        event.eventType = "order.created";
        event.payload = "{\"eventType\":\"order.created\",\"data\":{}}";
        event.status = "queued";
        event.correlationId = "correlation";
        return event;
    }

    private DeliveryAttemptEntity attempt(DeliveryJob job, String status) {
        var attempt = new DeliveryAttemptEntity();
        attempt.id = UUID.randomUUID();
        attempt.eventId = job.eventId();
        attempt.jobId = job.jobId();
        attempt.attemptNumber = job.attempt();
        attempt.status = status;
        attempt.startedAt = NOW;
        return attempt;
    }
}
