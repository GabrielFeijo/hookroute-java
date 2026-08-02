package com.gabrielfeijo.hookroute.api.controller;

import com.gabrielfeijo.hookroute.infrastructure.service.EventQueryService;
import com.gabrielfeijo.hookroute.infrastructure.service.EventReplayService;
import com.gabrielfeijo.hookroute.api.http.ApiBadRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
@SecurityRequirement(name = "admin-api-key")
@ApiResponse(responseCode = "401", description = "Chave administrativa ausente ou inválida.")
public class EventsController {
    private static final Set<String> STATUSES = Set.of("received", "queued", "processing", "delivered", "failed", "dead_lettered");
    private final EventQueryService queries;
    private final EventReplayService replay;

    public EventsController(EventQueryService queries, EventReplayService replay) {
        this.queries = queries;
        this.replay = replay;
    }

    @GetMapping
    @Operation(summary = "Lista eventos recebidos com filtros e paginação simples")
    public Map<String, Object> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String jobId,
            @RequestParam(defaultValue = "25") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        validateStatus(status);
        validatePage(limit, offset);
        return queries.list(status, eventType, jobId, limit, offset, false);
    }

    @GetMapping("/failures")
    @Operation(summary = "Lista eventos com falha ou enviados para DLQ")
    public Map<String, Object> failures(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String jobId,
            @RequestParam(defaultValue = "25") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        validatePage(limit, offset);
        return queries.list(null, eventType, jobId, limit, offset, true);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta um evento pelo identificador de auditoria")
    public Map<String, Object> find(@PathVariable String id) {
        return queries.find(id).orElseThrow(EventReplayService.EventNotFoundException::new);
    }

    @PostMapping("/{id}/replay")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Reenfileira um evento salvo para nova tentativa de processamento")
    public Map<String, Object> replay(@PathVariable String id) {
        return replay.replay(id);
    }

    private void validateStatus(String status) {
        if (status != null && !STATUSES.contains(status)) throw new ApiBadRequestException("Invalid webhook event status.");
    }

    private void validatePage(int limit, int offset) {
        if (limit < 1 || limit > 100) throw new ApiBadRequestException("limit must be between 1 and 100.");
        if (offset < 0) throw new ApiBadRequestException("offset must not be negative.");
    }
}
