package io.github.marciomarinho.quotient.ledger.application;

import io.github.marciomarinho.quotient.common.tenant.TenantId;
import io.github.marciomarinho.quotient.ledger.domain.AccountType;
import java.util.List;

/**
 * The outcome of a ledger verification pass: for every tenant, whether the materialized {@code
 * account_balance} rows equal the balances recomputed from raw entries. If {@link #allMatch()} is
 * false the ledger has drifted and the discrepancies pinpoint where.
 */
public record VerificationReport(boolean allMatch, List<TenantVerification> tenants) {

  public VerificationReport {
    tenants = List.copyOf(tenants);
  }

  /** Per-tenant verification result. */
  public record TenantVerification(
      TenantId tenantId, boolean match, List<Discrepancy> discrepancies) {
    public TenantVerification {
      discrepancies = List.copyOf(discrepancies);
    }
  }

  /** A single account whose materialized balance disagrees with the recomputed one. */
  public record Discrepancy(
      AccountType accountType, long materializedMinor, long recomputedMinor) {}
}
