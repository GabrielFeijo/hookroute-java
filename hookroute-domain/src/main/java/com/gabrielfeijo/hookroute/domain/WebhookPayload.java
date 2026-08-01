package com.gabrielfeijo.hookroute.domain;

import java.time.Instant;
import java.util.Map;

public record WebhookPayload(
        String eventType,
        Map<String, Object> data,
        Instant receivedAt,
        String source,
        String idempotencyKey) {
}
