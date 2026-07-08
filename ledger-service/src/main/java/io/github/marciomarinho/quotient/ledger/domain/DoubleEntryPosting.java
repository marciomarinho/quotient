package io.github.marciomarinho.quotient.ledger.domain;

import io.github.marciomarinho.quotient.common.event.Charge;
import io.github.marciomarinho.quotient.common.money.Money;
import java.util.List;

/**
 * Turns a {@link Charge} into the balanced double-entry {@link PostingPlan} the ledger records.
 *
 * <p>The charge amount is the net (ex-tax) revenue. GST is added on top, so the customer's
 * receivable is revenue + tax. The three lines are:
 *
 * <pre>
 *   DEBIT  RECEIVABLE  revenue + tax   (asset up: the customer owes us)
 *   CREDIT REVENUE     revenue         (income earned)
 *   CREDIT TAX         tax             (GST liability to the tax office)
 * </pre>
 *
 * which is balanced by construction (debits = revenue + tax = credits). Deterministic: same charge
 * + same GST policy always yields the same plan, and the transaction id is the charge's
 * deterministic id, so posting is idempotent.
 */
public final class DoubleEntryPosting {

  private final GstPolicy gstPolicy;

  public DoubleEntryPosting(GstPolicy gstPolicy) {
    this.gstPolicy = gstPolicy;
  }

  /** Build the posting plan for a charge. */
  public PostingPlan forCharge(Charge charge) {
    Money revenue = charge.amount();
    Money tax = gstPolicy.on(revenue);
    Money receivable = revenue.plus(tax);

    List<PostingLine> lines =
        List.of(
            PostingLine.debit(AccountType.RECEIVABLE, receivable),
            PostingLine.credit(AccountType.REVENUE, revenue),
            PostingLine.credit(AccountType.TAX, tax));

    String description =
        "usage charge: meter=%s window=[%s,%s) qty=%d plan=v%d"
            .formatted(
                charge.meterCode(),
                charge.window().start(),
                charge.window().end(),
                charge.quantityBilled(),
                charge.planVersion());

    return new PostingPlan(
        charge.chargeId(),
        charge.tenantId(),
        description,
        charge.chargeId(),
        revenue.currency(),
        lines);
  }
}
