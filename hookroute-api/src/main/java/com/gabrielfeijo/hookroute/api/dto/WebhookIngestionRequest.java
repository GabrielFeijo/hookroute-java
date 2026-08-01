package com.gabrielfeijo.hookroute.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = false)
public record WebhookIngestionRequest(String eventId, String eventType, Map<String, Object> data) {
}
