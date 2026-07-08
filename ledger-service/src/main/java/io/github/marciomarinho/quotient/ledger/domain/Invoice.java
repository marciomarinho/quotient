package io.github.marciomarinho.quotient.ledger.domain;

import io.github.marciomarinho.quotient.common.money.Money;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A generated invoice: the per-meter lines for a tenant over a period, plus the net / tax / total
 * roll-up. Assembled from the {@code billed_charge} records the ledger has already posted, so the
 * invoice is a statement over real ledger activity, not a new posting.
 */
public record Invoice(
    UUID id,
    TenantId tenantId,
    Instant periodStart,
    Instant periodEnd,
    Money net,
    Money tax,
    Money total,
    List<InvoiceLine> lines,
    String status) {

  public Invoice {
    lines = List.copyOf(lines);
  }

  /** One invoice line: total quantity and net amount for a meter over the period. */
  public record InvoiceLine(String meterCode, long quantity, Money amount) {}
}
