package io.github.marciomarinho.quotient.ingest.reactive.web;

import io.github.marciomarinho.quotient.ingest.reactive.ingest.InvalidUsageEventException;
import io.github.marciomarinho.quotient.ingest.reactive.publish.KafkaUnavailableException;
import io.github.marciomarinho.quotient.ingest.reactive.ratelimit.RateLimitExceededException;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

/** Maps ingestion errors to the contract's HTTP responses (reactive). */
@RestControllerAdvice
public class IngestExceptionHandler {

  private static final String RETRY_AFTER_SECONDS = "1";

  @ExceptionHandler(WebExchangeBindException.class)
  public ResponseEntity<Map<String, String>> handleInvalidBody(WebExchangeBindException e) {
    String detail =
        e.getFieldErrors().stream()
            .findFirst()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .orElse("invalid request body");
    return ResponseEntity.badRequest()
        .body(Map.of("error", "validation_failed", "message", detail));
  }

  @ExceptionHandler(InvalidUsageEventException.class)
  public ResponseEntity<Map<String, String>> handleInvalidEvent(InvalidUsageEventException e) {
    return ResponseEntity.unprocessableEntity()
        .body(Map.of("error", "invalid_event", "message", e.getMessage()));
  }

  @ExceptionHandler(RateLimitExceededException.class)
  public ResponseEntity<Map<String, String>> handleRateLimit(RateLimitExceededException e) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
        .body(Map.of("error", "rate_limited", "message", e.getMessage()));
  }

  @ExceptionHandler(KafkaUnavailableException.class)
  public ResponseEntity<Map<String, String>> handleKafkaDown(KafkaUnavailableException e) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
        .body(Map.of("error", "kafka_unavailable", "message", e.getMessage()));
  }
}
