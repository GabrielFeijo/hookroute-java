package com.gabrielfeijo.hookroute.infrastructure.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gabrielfeijo.hookroute.infrastructure.config.HookRouteProperties;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

    @Test
    void acceptsRedirectResponsesLikeTheReferenceImplementation() {
        var properties = new HookRouteProperties();
        properties.getEgress().setSimulatedLatencyMs(0);
        var mapper = new ObjectMapper();
        var service = new EgressService(HttpClient.newHttpClient(), mapper, properties, Clock.systemUTC());
        var payload = mapper.readTree("{\"data\":{\"simulate\":{\"statusCode\":302}}}");

        var result = service.dispatch("redirect", payload, "job", "correlation");

        assertEquals(302, result.statusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatchesPayloadToConfiguredRealDestination() throws Exception {
        var properties = new HookRouteProperties();
        properties.getEgress().setMode("real");
        properties.getEgress().setDestinationBaseUrl("https://receiver.example/hooks/");
        var httpClient = mock(HttpClient.class);
        var response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(204);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var mapper = new ObjectMapper();
        var service = new EgressService(httpClient, mapper, properties, Clock.systemUTC());
        var payload = mapper.readTree("{\"eventType\":\"order created\",\"data\":{\"orderId\":123}}");

        var result = service.dispatch("order created", payload, "job-123", "correlation-123");

        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("https://receiver.example/hooks/order%20created", request.getValue().uri().toString());
        assertEquals("application/json", request.getValue().headers().firstValue("Content-Type").orElseThrow());
        assertEquals("HookRoute/0.1.0", request.getValue().headers().firstValue("User-Agent").orElseThrow());
        assertEquals("job-123", request.getValue().headers().firstValue("Idempotency-Key").orElseThrow());
        assertEquals("correlation-123", request.getValue().headers().firstValue("X-Correlation-ID").orElseThrow());
        assertEquals(payload.toString(), requestBody(request.getValue()));
        assertEquals(204, result.statusCode());
    }

    @Test
    void keepsPayloadSimulationOverrideAvailableInRealMode() {
        var properties = new HookRouteProperties();
        properties.getEgress().setMode("real");
        properties.getEgress().setSimulatedLatencyMs(0);
        var httpClient = mock(HttpClient.class);
        var mapper = new ObjectMapper();
        var service = new EgressService(httpClient, mapper, properties, Clock.systemUTC());
        var payload = mapper.readTree("{\"data\":{\"simulate\":{\"statusCode\":202}}}");

        var result = service.dispatch("order.created", payload, "job", "correlation");

        assertEquals(202, result.statusCode());
        verifyNoInteractions(httpClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsRealRequestWhenPayloadSimulationOverrideIsDisabled() throws Exception {
        var properties = new HookRouteProperties();
        properties.getEgress().setMode("real");
        properties.getEgress().setAllowPayloadSimulation(false);
        var httpClient = mock(HttpClient.class);
        var response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var mapper = new ObjectMapper();
        var service = new EgressService(httpClient, mapper, properties, Clock.systemUTC());

        service.dispatch("order.created",
                mapper.readTree("{\"data\":{\"simulate\":{\"statusCode\":202}}}"), "job", "correlation");

        verify(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private static String requestBody(HttpRequest request) {
        var result = new CompletableFuture<String>();
        var bytes = new java.io.ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                bytes.writeBytes(chunk);
            }

            @Override
            public void onError(Throwable throwable) {
                result.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                result.complete(bytes.toString(StandardCharsets.UTF_8));
            }
        });
        return result.join();
    }
}
