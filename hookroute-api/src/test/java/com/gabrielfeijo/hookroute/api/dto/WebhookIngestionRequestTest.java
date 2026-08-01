package com.gabrielfeijo.hookroute.api.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.validation.Validation;
import java.util.Map;
import java.util.Set;
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

    @Test
    void rejectsUnknownRootFields() {
        assertThrows(RuntimeException.class, () -> new ObjectMapper().readValue(
                "{\"eventType\":\"order.created\",\"data\":{},\"unsupported\":true}",
                WebhookIngestionRequest.class));
    }

    @Test
    void validatesRequiredAndOptionalFields() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();

            assertTrue(validator.validate(new WebhookIngestionRequest(null, "order.created", Map.of())).isEmpty());
            assertEquals(1, validator.validate(new WebhookIngestionRequest(null, " ", Map.of())).size());
            assertEquals(1, validator.validate(new WebhookIngestionRequest(null, "order.created", null)).size());
            assertEquals(1, validator.validate(new WebhookIngestionRequest(" ", "order.created", Map.of())).size());
        }
    }

    @Test
    void exposesTypedFieldsInOpenApiSchema() {
        var schemas = ModelConverters.getInstance().read(WebhookIngestionRequest.class);
        var schema = schemas.get("WebhookIngestionRequest");
        var dataSchema = (Schema<?>) schema.getProperties().get("data");

        assertEquals(Set.of("eventId", "eventType", "data"), schema.getProperties().keySet());
        assertEquals("object", dataSchema.getType());
    }
}
