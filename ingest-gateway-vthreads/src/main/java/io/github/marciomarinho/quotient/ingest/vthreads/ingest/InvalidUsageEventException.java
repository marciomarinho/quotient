package io.github.marciomarinho.quotient.ingest.vthreads.ingest;

/**
 * A usage event failed a business validation rule (unknown meter, future timestamp). Maps to 422.
 */
public class InvalidUsageEventException extends RuntimeException {

  public InvalidUsageEventException(String message) {
    super(message);
  }
}
