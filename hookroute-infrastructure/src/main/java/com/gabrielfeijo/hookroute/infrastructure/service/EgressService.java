package com.gabrielfeijo.hookroute.infrastructure.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.gabrielfeijo.hookroute.infrastructure.config.HookRouteProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

@Service
public class EgressService {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final HookRouteProperties.Egress properties;
    private final Clock clock;
    private final Map<String, Circuit> circuits = new ConcurrentHashMap<>();

    public EgressService(HttpClient httpClient, ObjectMapper objectMapper, HookRouteProperties root, Clock clock) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = root.getEgress();
        this.clock = clock;
    }

    public DispatchResult dispatch(String eventType, JsonNode payload, String jobId, String correlationId) {
        String destination = destination(eventType);
        Circuit circuit = circuits.computeIfAbsent(destination, ignored -> new Circuit());
        String initialState;
        synchronized (circuit) {
            if (circuit.state.equals("open") && circuit.openedUntil <= clock.millis()) circuit.state = "half_open";
            if (circuit.state.equals("open")) throw new EgressException("Circuit breaker is open for " + destination);
            initialState = circuit.state;
        }
        long startedAt = clock.millis();
        try {
            int statusCode = dispatchPayload(destination, payload, jobId, correlationId);
            requireSuccessfulStatus(statusCode);
            synchronized (circuit) {
                circuit.state = "closed";
                circuit.failures = 0;
                circuit.openedUntil = 0;
            }
            return new DispatchResult(destination, statusCode, clock.millis() - startedAt, initialState);
        } catch (RuntimeException error) {
            synchronized (circuit) {
                circuit.failures++;
                if (circuit.failures >= properties.getCircuitFailureThreshold()) {
                    circuit.state = "open";
                    circuit.openedUntil = clock.millis() + properties.getCircuitOpenMs();
                }
            }
            throw error;
        }
    }

    private void requireSuccessfulStatus(int statusCode) {
        if (statusCode < 400) return;
        if (statusCode >= 500) {
            throw new EgressException("Egress destination failed with status " + statusCode);
        }
        throw new EgressException("Egress destination rejected payload with status " + statusCode);
    }

    public String destination(String eventType) {
        String base = properties.getDestinationBaseUrl().replaceAll("/+$", "");
        return base + "/" + UriUtils.encodePathSegment(eventType, java.nio.charset.StandardCharsets.UTF_8);
    }

    public CircuitSnapshot circuitSnapshot(String destination) {
        Circuit circuit = circuits.computeIfAbsent(destination, ignored -> new Circuit());
        synchronized (circuit) {
            if (circuit.state.equals("open") && circuit.openedUntil <= clock.millis()) circuit.state = "half_open";
            return new CircuitSnapshot(circuit.state, circuit.failures,
                    circuit.openedUntil > clock.millis() ? java.time.Instant.ofEpochMilli(circuit.openedUntil).toString() : null);
        }
    }

    private int dispatchPayload(String destination, JsonNode payload, String jobId, String correlationId) {
        JsonNode simulation = payload.path("data").path("simulate");
        boolean requestedSimulation = properties.isAllowPayloadSimulation() && simulation.isObject()
                && ((simulation.path("statusCode").isNumber() && simulation.path("statusCode").asInt() != 0)
                || (simulation.path("delayMs").isNumber() && simulation.path("delayMs").asLong() != 0));
        if (!"real".equals(properties.getMode()) || requestedSimulation) return dispatchSimulated(payload);

        try {
            var request = HttpRequest.newBuilder(URI.create(destination))
                    .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", "HookRoute/0.1.0")
                    .header("Idempotency-Key", jobId)
                    .header("X-Correlation-ID", correlationId == null ? "" : correlationId)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (java.net.http.HttpTimeoutException error) {
            throw new EgressException("Egress dispatch timed out after " + properties.getTimeoutMs() + "ms", error);
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new EgressException(error.getMessage() == null ? "Egress dispatch failed" : error.getMessage(), error);
        }
    }

    private int dispatchSimulated(JsonNode payload) {
        JsonNode simulation = payload.path("data").path("simulate");
        boolean payloadSimulation = properties.isAllowPayloadSimulation() && simulation.isObject();
        int status = payloadSimulation && simulation.path("statusCode").isNumber()
                ? simulation.path("statusCode").asInt() : 200;
        long delay = payloadSimulation && simulation.path("delayMs").isNumber()
                ? simulation.path("delayMs").asLong() : properties.getSimulatedLatencyMs();
        if (delay > properties.getTimeoutMs()) {
            sleep(properties.getTimeoutMs());
            throw new EgressException("Egress dispatch timed out after " + properties.getTimeoutMs() + "ms");
        }
        sleep(Math.max(0, delay));
        return status;
    }

    private void sleep(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new EgressException("Egress dispatch interrupted", error);
        }
    }

    private static final class Circuit {
        String state = "closed";
        int failures;
        long openedUntil;
    }

    public record DispatchResult(String destination, int statusCode, long durationMs, String circuitState) {}
    public record CircuitSnapshot(String state, int consecutiveFailures, String openedUntil) {}
    public static class EgressException extends RuntimeException {
        public EgressException(String message) { super(message); }
        public EgressException(String message, Throwable cause) { super(message, cause); }
    }
}
