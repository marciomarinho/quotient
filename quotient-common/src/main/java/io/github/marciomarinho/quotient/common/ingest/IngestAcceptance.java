package io.github.marciomarinho.quotient.common.ingest;

/**
 * The per-event result of ingestion, returned with HTTP 202.
 *
 * @param idempotencyKey echoes the event's key
 * @param deduplicated {@code true} if this key was already seen within the dedup window and the
 *     event was therefore not re-published; {@code false} if it was accepted and published to Kafka
 */
public record IngestAcceptance(String idempotencyKey, boolean deduplicated) {

  public static IngestAcceptance accepted(String idempotencyKey) {
    return new IngestAcceptance(idempotencyKey, false);
  }

  public static IngestAcceptance deduplicated(String idempotencyKey) {
    return new IngestAcceptance(idempotencyKey, true);
  }
}
