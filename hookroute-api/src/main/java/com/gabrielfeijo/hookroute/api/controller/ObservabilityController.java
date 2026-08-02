package com.gabrielfeijo.hookroute.api.controller;

import com.gabrielfeijo.hookroute.infrastructure.service.ObservabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ObservabilityController {
    private final ObservabilityService observability;

    public ObservabilityController(ObservabilityService observability) {
        this.observability = observability;
    }

    @GetMapping("/health")
    @Operation(summary = "Verifica saúde básica de banco e filas")
    public Map<String, Object> health() { return observability.health(); }

    @GetMapping("/metrics")
    @Operation(summary = "Retorna métricas básicas de filas e eventos")
    @SecurityRequirement(name = "admin-api-key")
    @ApiResponse(responseCode = "401", description = "Chave administrativa ausente ou inválida.")
    public Map<String, Object> metrics() { return observability.metrics(); }
}
