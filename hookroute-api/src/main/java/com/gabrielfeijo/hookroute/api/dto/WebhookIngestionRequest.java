package com.gabrielfeijo.hookroute.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Map;

@Schema(description = "Webhook accepted for asynchronous delivery")
public record WebhookIngestionRequest(
        @Pattern(regexp = ".*\\S.*", message = "must be a non-empty string when provided")
        @Schema(description = "Producer idempotency key", example = "evt_123")
        String eventId,
        @NotBlank
        @Schema(description = "Event type appended to the configured destination", example = "order.created")
        String eventType,
        @NotNull
        @Schema(description = "Event-specific JSON object")
        Map<String, Object> data) {

    @JsonAnySetter
    public void rejectUnknownField(String field, Object ignoredValue) {
        throw new IllegalArgumentException("Unsupported root field: " + field);
    }
}
