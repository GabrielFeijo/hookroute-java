package com.gabrielfeijo.hookroute.infrastructure.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.gabrielfeijo.hookroute.infrastructure.config.HookRouteProperties;
import java.net.http.HttpClient;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class EgressServiceTest {
    @Test
    void simulatesSuccessfulDispatchAndEncodesDestination() {
        var properties = new HookRouteProperties();
        properties.getEgress().setSimulatedLatencyMs(0);
        var service = new EgressService(HttpClient.newHttpClient(), new ObjectMapper(), properties, Clock.systemUTC());
        var payload = new ObjectMapper().readTree("{\"data\":{}}");

        var result = service.dispatch("order created", payload, "job", "correlation");

        assertEquals(200, result.statusCode());
        assertEquals("https://example.com/webhooks/order%20created", result.destination());
    }

    @Test
    void opensCircuitAfterConfiguredFailures() {
        var properties = new HookRouteProperties();
        properties.getEgress().setSimulatedLatencyMs(0);
        properties.getEgress().setCircuitFailureThreshold(2);
        var mapper = new ObjectMapper();
        var service = new EgressService(HttpClient.newHttpClient(), mapper, properties, Clock.systemUTC());
        var payload = mapper.readTree("{\"data\":{\"simulate\":{\"statusCode\":503}}}");

        assertThrows(EgressService.EgressException.class, () -> service.dispatch("failed", payload, "job", "c"));
        assertThrows(EgressService.EgressException.class, () -> service.dispatch("failed", payload, "job", "c"));

        assertEquals("open", service.circuitSnapshot(service.destination("failed")).state());
    }
}
