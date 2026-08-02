package com.gabrielfeijo.hookroute.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.gabrielfeijo.hookroute.api.controller.EventsController;
import com.gabrielfeijo.hookroute.api.controller.ObservabilityController;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

class OpenApiConfigurationTest {
    @Test
    void declaresAdministrativeApiKeySchemeAndProtectedOperations() throws Exception {
        var openApi = new OpenApiConfiguration().hookRouteOpenApi();
        var scheme = openApi.getComponents().getSecuritySchemes().get("admin-api-key");

        assertEquals(SecurityScheme.Type.APIKEY, scheme.getType());
        assertEquals(SecurityScheme.In.HEADER, scheme.getIn());
        assertEquals("x-admin-api-key", scheme.getName());
        assertEquals("admin-api-key",
                EventsController.class.getAnnotation(SecurityRequirement.class).name());
        assertEquals("admin-api-key", ObservabilityController.class.getMethod("metrics")
                .getAnnotation(SecurityRequirement.class).name());
        assertFalse(ObservabilityController.class.getMethod("health")
                .isAnnotationPresent(SecurityRequirement.class));
    }
}
