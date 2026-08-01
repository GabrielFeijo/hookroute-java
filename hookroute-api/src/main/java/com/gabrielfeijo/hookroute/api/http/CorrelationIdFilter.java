package com.gabrielfeijo.hookroute.api.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader("x-correlation-id");
        String correlationId = incoming == null || incoming.isBlank() ? UUID.randomUUID().toString() : incoming;
        request.setAttribute("correlationId", correlationId);
        response.setHeader("x-correlation-id", correlationId);
        MDC.put("correlationId", correlationId);
        try { chain.doFilter(request, response); }
        finally { MDC.remove("correlationId"); }
    }
}
