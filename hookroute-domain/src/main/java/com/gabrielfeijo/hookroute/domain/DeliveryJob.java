package com.gabrielfeijo.hookroute.domain;

import java.util.UUID;

public record DeliveryJob(UUID jobId, UUID eventId, int attempt, String correlationId) {
    public static final int MAX_ATTEMPTS = 5;

    public DeliveryJob {
        if (attempt < 1 || attempt > MAX_ATTEMPTS) {
            throw new IllegalArgumentException("attempt must be between 1 and " + MAX_ATTEMPTS);
        }
    }

    public long nextBackoffMillis() {
        return 1_000L << (attempt - 1);
    }
}
