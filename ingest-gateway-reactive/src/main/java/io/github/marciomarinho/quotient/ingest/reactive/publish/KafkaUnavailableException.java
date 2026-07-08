package io.github.marciomarinho.quotient.ingest.reactive.publish;

/**
 * Thrown when an event cannot be confirmed as published to Kafka (broker down, ack timeout). The
 * API maps this to 503 with a {@code Retry-After} header — the gateway never buffers unbounded in
 * memory.
 */
public class KafkaUnavailableException extends RuntimeException {

  public KafkaUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
