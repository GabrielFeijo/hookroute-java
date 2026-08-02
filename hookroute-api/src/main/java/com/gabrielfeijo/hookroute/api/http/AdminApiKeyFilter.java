package com.gabrielfeijo.hookroute.api.http;

import com.gabrielfeijo.hookroute.infrastructure.config.HookRouteProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AdminApiKeyFilter extends OncePerRequestFilter {
    static final String HEADER_NAME = "x-admin-api-key";
    private static final String ERROR_MESSAGE = "Invalid administrative API key.";

    private final HookRouteProperties.Security security;
    private final ObjectMapper objectMapper;

    public AdminApiKeyFilter(HookRouteProperties properties, ObjectMapper objectMapper) {
        this.security = properties.getSecurity();
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.equals("/api/metrics")
                && !path.equals("/api/events")
                && !path.startsWith("/api/events/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String receivedApiKey = singleHeader(request);
        String configuredApiKey = security.getAdminApiKey();
        if (configuredApiKey == null || configuredApiKey.isBlank()
                || receivedApiKey == null || !safeCompare(receivedApiKey, configuredApiKey)) {
            unauthorized(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private String singleHeader(HttpServletRequest request) {
        var values = Collections.list(request.getHeaders(HEADER_NAME));
        return values.size() == 1 ? values.getFirst() : null;
    }

    private boolean safeCompare(String received, String expected) {
        byte[] receivedBytes = received.getBytes(StandardCharsets.UTF_8);
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        return receivedBytes.length == expectedBytes.length && MessageDigest.isEqual(receivedBytes, expectedBytes);
    }

    private void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), java.util.Map.of(
                "statusCode", 401, "message", ERROR_MESSAGE, "error", "Unauthorized"));
    }
}
