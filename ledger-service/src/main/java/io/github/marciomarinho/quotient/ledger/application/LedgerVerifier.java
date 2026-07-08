package io.github.marciomarinho.quotient.ledger.application;

import io.github.marciomarinho.quotient.common.tenant.TenantId;
import io.github.marciomarinho.quotient.ledger.application.VerificationReport.TenantVerification;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Re-computes every tenant's account balances from raw ledger entries and asserts they match the
 * materialized balances. Backs the {@code ledger-verify} task run in CI (Section 10 of the project
 * plan): a green result is proof that no posting has silently corrupted the derived balances.
 */
@Service
public class LedgerVerifier {

  private final LedgerRepository repository;
  private final TenantVerifier tenantVerifier;

  public LedgerVerifier(LedgerRepository repository, TenantVerifier tenantVerifier) {
    this.repository = repository;
    this.tenantVerifier = tenantVerifier;
  }

  /** Verify all tenants. {@link VerificationReport#allMatch()} is the pass/fail bit. */
  public VerificationReport verifyAll() {
    List<TenantId> tenantIds = repository.allTenantIds();
    List<TenantVerification> results = tenantIds.stream().map(tenantVerifier::verify).toList();
    boolean allMatch = results.stream().allMatch(TenantVerification::match);
    return new VerificationReport(allMatch, results);
  }
}
