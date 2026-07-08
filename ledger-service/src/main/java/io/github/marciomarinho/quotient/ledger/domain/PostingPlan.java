package io.github.marciomarinho.quotient.ledger.domain;

import io.github.marciomarinho.quotient.common.money.Currency;
import io.github.marciomarinho.quotient.common.money.Money;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A fully-formed, balanced double-entry transaction ready to post.
 *
 * <p>This is layer 2 of the balance invariant (the application layer): the plan <em>cannot be
 * constructed</em> unless it has at least two lines, a single currency, and equal debit and credit
 * totals. So an unbalanced posting is unrepresentable in the domain, independently of the database
 * trigger that also enforces it (layer 1) and the idempotent insert that prevents replay (layer 3).
 *
 * @param transactionId deterministic id (the source charge id) — the idempotency key
 * @param sourceChargeId the charge that produced this posting, for audit (may equal transactionId)
 */
public record PostingPlan(
    UUID transactionId,
    TenantId tenantId,
    String description,
    UUID sourceChargeId,
    Currency currency,
    List<PostingLine> lines) {

  public PostingPlan {
    Objects.requireNonNull(transactionId, "transactionId");
    Objects.requireNonNull(tenantId, "tenantId");
    Objects.requireNonNull(description, "description");
    Objects.requireNonNull(currency, "currency");
    Objects.requireNonNull(lines, "lines");
    lines = List.copyOf(lines);

    if (lines.size() < 2) {
      throw new IllegalArgumentException(
          "a double-entry posting needs >= 2 lines, had " + lines.size());
    }
    Money debits = Money.zero(currency);
    Money credits = Money.zero(currency);
    for (PostingLine line : lines) {
      if (line.amount().currency() != currency) {
        throw new IllegalArgumentException(
            "posting line currency " + line.amount().currency() + " != plan currency " + currency);
      }
      if (line.direction() == EntryDirection.DEBIT) {
        debits = debits.plus(line.amount());
      } else {
        credits = credits.plus(line.amount());
      }
    }
    if (!debits.equals(credits)) {
      throw new IllegalArgumentException(
          "posting is unbalanced: debits=" + debits + " credits=" + credits);
    }
  }
}
