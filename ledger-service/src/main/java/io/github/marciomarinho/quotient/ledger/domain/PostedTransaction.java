package io.github.marciomarinho.quotient.ledger.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A recorded ledger transaction with its entries, for the query API and audit. */
public record PostedTransaction(
    UUID id, String description, Instant createdAt, List<PostedEntry> entries) {

  public PostedTransaction {
    entries = List.copyOf(entries);
  }
}
