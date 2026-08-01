package com.gabrielfeijo.hookroute.application;

import java.util.Map;

public record IngestionResult(boolean duplicate, String jobId, String eventId, Map<String, Object> payload) {
}
