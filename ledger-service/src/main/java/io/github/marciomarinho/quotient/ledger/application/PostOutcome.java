package io.github.marciomarinho.quotient.ledger.application;

/** Result of attempting to post a charge to the ledger. */
public enum PostOutcome {
  /** The transaction was newly recorded. */
  POSTED,
  /**
   * A transaction with this deterministic id already existed; nothing changed (idempotent replay).
   */
  DUPLICATE
}
