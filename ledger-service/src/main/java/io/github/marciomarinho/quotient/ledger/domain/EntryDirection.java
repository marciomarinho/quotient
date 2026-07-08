package io.github.marciomarinho.quotient.ledger.domain;

/**
 * Which side of the ledger an entry falls on. The signed contribution of an entry to an account
 * balance is {@code +amount} for a DEBIT and {@code -amount} for a CREDIT when the account's normal
 * balance is DEBIT (and vice versa) — see {@link AccountType#normalBalance()} and the balance
 * computation in the verifier.
 */
public enum EntryDirection {
  DEBIT,
  CREDIT;

  /** The opposite side. */
  public EntryDirection opposite() {
    return this == DEBIT ? CREDIT : DEBIT;
  }
}
