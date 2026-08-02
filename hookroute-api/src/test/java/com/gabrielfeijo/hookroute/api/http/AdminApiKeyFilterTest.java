package com.gabrielfeijo.hookroute.api.http;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gabrielfeijo.hookroute.api.controller.EventsController;
import com.gabrielfeijo.hookroute.api.controller.ObservabilityController;
import com.gabrielfeijo.hookroute.infrastructure.config.HookRouteProperties;
import com.gabrielfeijo.hookroute.infrastructure.service.EventQueryService;
import com.gabrielfeijo.hookroute.infrastructure.service.EventReplayService;
import com.gabrielfeijo.hookroute.infrastructure.service.ObservabilityService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

class AdminApiKeyFilterTest {
    private static final String API_KEY = "admin-api-key-with-at-least-32-characters";

    @Test
    void protectsMetricsAndEveryEventsEndpoint() throws Exception {
        var fixture = fixture(API_KEY);

        fixture.mvc.perform(get("/api/metrics"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.statusCode").value(401))
                .andExpect(jsonPath("$.message").value("Invalid administrative API key."))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
        fixture.mvc.perform(get("/api/events")).andExpect(status().isUnauthorized());
        fixture.mvc.perform(get("/api/events/failures")).andExpect(status().isUnauthorized());
        fixture.mvc.perform(post("/api/events/event-id/replay")).andExpect(status().isUnauthorized());

        verifyNoInteractions(fixture.observability, fixture.queries, fixture.replay);
    }

    @Test
    void rejectsWrongAndDuplicateCredentialsWithoutExposingTheSecret() throws Exception {
        var fixture = fixture(API_KEY);

        fixture.mvc.perform(get("/api/metrics")
                        .header(AdminApiKeyFilter.HEADER_NAME, "x".repeat(API_KEY.length())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid administrative API key."));
        fixture.mvc.perform(get("/api/metrics").header(AdminApiKeyFilter.HEADER_NAME, "wrong-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid administrative API key."));
        fixture.mvc.perform(get("/api/metrics")
                        .header(AdminApiKeyFilter.HEADER_NAME, API_KEY, API_KEY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsTheExactKeyAndLeavesHealthPublic() throws Exception {
        var fixture = fixture(API_KEY);
        when(fixture.observability.health()).thenReturn(Map.of("status", "ok"));
        when(fixture.observability.metrics()).thenReturn(Map.of("events", Map.of("total", 0)));
        when(fixture.queries.list(isNull(), isNull(), isNull(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(Map.of("total", 0, "items", java.util.List.of()));

        fixture.mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
        fixture.mvc.perform(post("/api/ingestion/webhooks"))
                .andExpect(status().isNotFound());
        fixture.mvc.perform(get("/api/metrics").header(AdminApiKeyFilter.HEADER_NAME, API_KEY))
                .andExpect(status().isOk());
        fixture.mvc.perform(get("/api/events").header(AdminApiKeyFilter.HEADER_NAME, API_KEY))
                .andExpect(status().isOk());
    }

    @Test
    void failsClosedWhenNoAdministrativeKeyIsConfigured() throws Exception {
        var fixture = fixture("");

        fixture.mvc.perform(get("/api/metrics").header(AdminApiKeyFilter.HEADER_NAME, API_KEY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(fixture.observability);
    }

    private Fixture fixture(String configuredApiKey) {
        var properties = new HookRouteProperties();
        properties.getSecurity().setAdminApiKey(configuredApiKey);
        var observability = mock(ObservabilityService.class);
        var queries = mock(EventQueryService.class);
        var replay = mock(EventReplayService.class);
        var filter = new AdminApiKeyFilter(properties, new ObjectMapper());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new ObservabilityController(observability), new EventsController(queries, replay))
                .addFilters(filter)
                .build();
        return new Fixture(mvc, observability, queries, replay);
    }

    private record Fixture(MockMvc mvc, ObservabilityService observability,
                           EventQueryService queries, EventReplayService replay) {}
}
