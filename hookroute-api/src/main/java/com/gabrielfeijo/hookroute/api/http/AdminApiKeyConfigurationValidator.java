package com.gabrielfeijo.hookroute.api.http;

import com.gabrielfeijo.hookroute.infrastructure.config.HookRouteProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class AdminApiKeyConfigurationValidator {
    static final int MINIMUM_KEY_LENGTH = 32;

    private final HookRouteProperties.Security security;
    private final Environment environment;

    public AdminApiKeyConfigurationValidator(HookRouteProperties properties, Environment environment) {
        this.security = properties.getSecurity();
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        String apiKey = security.getAdminApiKey();
        boolean missing = apiKey == null || apiKey.isBlank();
        if (!missing && apiKey.length() < MINIMUM_KEY_LENGTH) {
            throw new IllegalStateException("ADMIN_API_KEY must contain at least 32 characters when configured.");
        }
        if (missing && environment.acceptsProfiles(Profiles.of("production"))) {
            throw new IllegalStateException(
                    "ADMIN_API_KEY is required in production and must contain at least 32 characters.");
        }
    }
}
