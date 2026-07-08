package io.github.marciomarinho.quotient.ledger.domain;

/**
 * The kinds of ledger account a tenant holds.
 *
 * <p>Normal balance conventions (which side increases the account) are encoded so the posting logic
 * and balance maths agree in one place:
 *
 * <ul>
 *   <li>{@code RECEIVABLE} — an asset; increased by DEBIT (what the customer owes us).
 *   <li>{@code REVENUE} — income; increased by CREDIT (what we earned, ex-tax).
 *   <li>{@code TAX} — a liability; increased by CREDIT (GST we owe the tax office).
 *   <li>{@code CREDITS} — a liability; increased by CREDIT (prepaid balance we owe the tenant).
 * </ul>
 */
public enum AccountType {
  RECEIVABLE(EntryDirection.DEBIT),
  REVENUE(EntryDirection.CREDIT),
  TAX(EntryDirection.CREDIT),
  CREDITS(EntryDirection.CREDIT);

  private final EntryDirection normalBalance;

  AccountType(EntryDirection normalBalance) {
    this.normalBalance = normalBalance;
  }

  /** The direction that increases this account's balance. */
  public EntryDirection normalBalance() {
    return normalBalance;
  }
}
