package com.gabrielfeijo.hookroute.api.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class WebhookIngestionRequestTest {
    @Test
    void deserializesThePublicContract() {
        var request = new ObjectMapper().readValue(
                "{\"eventId\":\"evt_1\",\"eventType\":\"order.created\",\"data\":{\"id\":\"1\"}}",
                WebhookIngestionRequest.class);
        assertEquals("evt_1", request.eventId());
        assertEquals("order.created", request.eventType());
        assertEquals("1", request.data().get("id"));
    }
}
