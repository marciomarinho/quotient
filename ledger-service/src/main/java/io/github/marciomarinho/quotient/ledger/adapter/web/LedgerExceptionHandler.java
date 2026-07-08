package io.github.marciomarinho.quotient.ledger.adapter.web;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps common ledger API errors to HTTP responses. */
@RestControllerAdvice
public class LedgerExceptionHandler {

  /** No tenant bound (missing/invalid identity) -> 400 rather than a 500. */
  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String, String>> handleNoTenant(IllegalStateException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("error", "tenant_context_required", "message", e.getMessage()));
  }
}
