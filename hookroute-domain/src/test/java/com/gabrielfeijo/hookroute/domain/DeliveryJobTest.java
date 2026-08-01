package com.gabrielfeijo.hookroute.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeliveryJobTest {
    @Test
    void calculatesExponentialBackoff() {
        UUID job = UUID.randomUUID();
        UUID event = UUID.randomUUID();
        assertEquals(1_000, new DeliveryJob(job, event, 1, "correlation").nextBackoffMillis());
        assertEquals(8_000, new DeliveryJob(job, event, 4, "correlation").nextBackoffMillis());
    }

    @Test
    void rejectsAttemptsOutsideTheContract() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeliveryJob(UUID.randomUUID(), UUID.randomUUID(), 6, null));
    }
}
