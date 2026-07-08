package io.github.marciomarinho.quotient.ledger.domain;

import io.github.marciomarinho.quotient.common.money.Money;
import java.util.Objects;

/**
 * One line of a posting: a debit or credit of a positive amount against a kind of account. The
 * amount is always non-negative; {@link #direction()} carries the sign.
 */
public record PostingLine(AccountType accountType, EntryDirection direction, Money amount) {

  public PostingLine {
    Objects.requireNonNull(accountType, "accountType");
    Objects.requireNonNull(direction, "direction");
    Objects.requireNonNull(amount, "amount");
    if (amount.isNegative()) {
      throw new IllegalArgumentException("posting line amount must be >= 0, was " + amount);
    }
  }

  public static PostingLine debit(AccountType accountType, Money amount) {
    return new PostingLine(accountType, EntryDirection.DEBIT, amount);
  }

  public static PostingLine credit(AccountType accountType, Money amount) {
    return new PostingLine(accountType, EntryDirection.CREDIT, amount);
  }
}
