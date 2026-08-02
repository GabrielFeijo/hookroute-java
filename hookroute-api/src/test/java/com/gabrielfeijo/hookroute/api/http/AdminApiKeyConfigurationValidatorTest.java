package com.gabrielfeijo.hookroute.api.http;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gabrielfeijo.hookroute.infrastructure.config.HookRouteProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

class AdminApiKeyConfigurationValidatorTest {
    @Test
    void allowsMissingKeyOutsideProductionSoProtectedEndpointsCanFailClosed() {
        assertDoesNotThrow(() -> validator("", false).validate());
    }

    @Test
    void requiresAdministrativeKeyInProduction() {
        assertThrows(IllegalStateException.class, () -> validator("", true).validate());
        assertThrows(IllegalStateException.class, () -> validator("   ", true).validate());
    }

    @Test
    void rejectsShortConfiguredKeyInEveryEnvironment() {
        assertThrows(IllegalStateException.class, () -> validator("short-key", false).validate());
    }

    @Test
    void acceptsStrongConfiguredKeyInProduction() {
        assertDoesNotThrow(() -> validator("admin-api-key-with-at-least-32-characters", true).validate());
    }

    private AdminApiKeyConfigurationValidator validator(String apiKey, boolean production) {
        var properties = new HookRouteProperties();
        properties.getSecurity().setAdminApiKey(apiKey);
        Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(production);
        return new AdminApiKeyConfigurationValidator(properties, environment);
    }
}
