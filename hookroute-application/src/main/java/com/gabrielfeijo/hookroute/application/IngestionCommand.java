package com.gabrielfeijo.hookroute.application;

import com.gabrielfeijo.hookroute.domain.WebhookPayload;

public record IngestionCommand(WebhookPayload payload, String correlationId) {
}
