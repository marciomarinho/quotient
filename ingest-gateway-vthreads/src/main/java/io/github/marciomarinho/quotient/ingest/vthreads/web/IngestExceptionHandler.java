package io.github.marciomarinho.quotient.ingest.vthreads.web;

import io.github.marciomarinho.quotient.ingest.vthreads.ingest.InvalidUsageEventException;
import io.github.marciomarinho.quotient.ingest.vthreads.publish.KafkaUnavailableException;
import io.github.marciomarinho.quotient.ingest.vthreads.ratelimit.RateLimitExceededException;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps ingestion errors to the contract's HTTP responses. */
@RestControllerAdvice
public class IngestExceptionHandler {

  private static final String RETRY_AFTER_SECONDS = "1";

  /** Bean-validation failures (missing fields, negative quantity, batch too large) -> 400. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> handleInvalidBody(MethodArgumentNotValidException e) {
    String detail =
        e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .orElse("invalid request body");
    return ResponseEntity.badRequest()
        .body(Map.of("error", "validation_failed", "message", detail));
  }

  /** Business validation (unknown meter, future timestamp) -> 422. */
  @ExceptionHandler(InvalidUsageEventException.class)
  public ResponseEntity<Map<String, String>> handleInvalidEvent(InvalidUsageEventException e) {
    return ResponseEntity.unprocessableEntity()
        .body(Map.of("error", "invalid_event", "message", e.getMessage()));
  }

  /** Tenant over its rate limit -> 429 with Retry-After. */
  @ExceptionHandler(RateLimitExceededException.class)
  public ResponseEntity<Map<String, String>> handleRateLimit(RateLimitExceededException e) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
        .body(Map.of("error", "rate_limited", "message", e.getMessage()));
  }

  /** Kafka unavailable / ack timeout -> 503 with Retry-After (never buffer unbounded). */
  @ExceptionHandler(KafkaUnavailableException.class)
  public ResponseEntity<Map<String, String>> handleKafkaDown(KafkaUnavailableException e) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
        .body(Map.of("error", "kafka_unavailable", "message", e.getMessage()));
  }
}
