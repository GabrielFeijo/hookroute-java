package com.gabrielfeijo.hookroute.api.http;

import com.gabrielfeijo.hookroute.infrastructure.service.EventReplayService;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(EventReplayService.EventNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound() { return error(HttpStatus.NOT_FOUND, "Event not found."); }

    @ExceptionHandler(EventReplayService.InvalidStoredPayloadException.class)
    ResponseEntity<Map<String, Object>> invalidStored() { return error(HttpStatus.BAD_REQUEST, "Stored event payload cannot be replayed."); }

    @ExceptionHandler(ApiBadRequestException.class)
    ResponseEntity<Map<String, Object>> badRequest(ApiBadRequestException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class})
    ResponseEntity<Map<String, Object>> invalidPayload() {
        return error(HttpStatus.BAD_REQUEST, "Payload validation failed.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalidArgument(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst().map(field -> field.getField() + " " + field.getDefaultMessage())
                .orElse("Payload validation failed.");
        return error(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "statusCode", status.value(), "message", message == null ? status.getReasonPhrase() : message,
                "error", status.getReasonPhrase()));
    }
}
