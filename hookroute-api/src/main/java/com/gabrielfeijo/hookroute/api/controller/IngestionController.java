package com.gabrielfeijo.hookroute.api.controller;

import com.gabrielfeijo.hookroute.application.IngestionCommand;
import com.gabrielfeijo.hookroute.application.port.IngestionPort;
import com.gabrielfeijo.hookroute.api.dto.WebhookIngestionRequest;
import com.gabrielfeijo.hookroute.domain.WebhookPayload;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ingestion")
public class IngestionController {
    private final IngestionPort ingestion;
    private final Clock clock;

    public IngestionController(IngestionPort ingestion, Clock clock) {
        this.ingestion = ingestion;
        this.clock = clock;
    }

    @PostMapping("/webhooks")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Recebe um webhook e publica o evento na fila de processamento")
    public Map<String, Object> receive(@Valid @RequestBody WebhookIngestionRequest request,
                                       HttpServletRequest servletRequest) {
        String correlationId = String.valueOf(servletRequest.getAttribute("correlationId"));
        var result = ingestion.ingest(new IngestionCommand(new WebhookPayload(
                request.eventType(), request.data(), clock.instant(), "ingestion-api", request.eventId()), correlationId));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("accepted", true);
        response.put("duplicate", result.duplicate());
        response.put("statusCode", 202);
        response.put("message", result.duplicate()
                ? "Payload already received. Returning existing event without queuing a duplicate job."
                : "Payload received and queued for downstream processing.");
        response.put("jobId", result.jobId());
        response.put("eventId", result.eventId());
        response.put("payload", result.payload());
        return response;
    }
}
