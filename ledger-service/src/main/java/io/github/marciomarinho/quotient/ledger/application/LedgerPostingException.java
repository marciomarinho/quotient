package io.github.marciomarinho.quotient.ledger.application;

/** Thrown when a charge cannot be posted (e.g. serialization retries exhausted). */
public class LedgerPostingException extends RuntimeException {

  public LedgerPostingException(String message, Throwable cause) {
    super(message, cause);
  }
}
