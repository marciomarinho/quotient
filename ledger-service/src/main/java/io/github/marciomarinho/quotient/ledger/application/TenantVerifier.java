package io.github.marciomarinho.quotient.ledger.application;

import io.github.marciomarinho.quotient.common.tenant.TenantId;
import io.github.marciomarinho.quotient.ledger.application.VerificationReport.Discrepancy;
import io.github.marciomarinho.quotient.ledger.application.VerificationReport.TenantVerification;
import io.github.marciomarinho.quotient.ledger.domain.AccountBalance;
import io.github.marciomarinho.quotient.ledger.domain.AccountType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies a single tenant's ledger within one read-only transaction so the materialized balances
 * and the recomputed balances are read from the same snapshot under the same tenant RLS binding.
 * Separate bean so the {@code @Transactional} proxy applies when {@link LedgerVerifier} calls it.
 */
@Component
public class TenantVerifier {

  private final LedgerRepository repository;

  public TenantVerifier(LedgerRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public TenantVerification verify(TenantId tenantId) {
    Map<AccountType, Long> materialized = byType(repository.balances(tenantId));
    Map<AccountType, Long> recomputed = byType(repository.recomputedBalances(tenantId));

    List<Discrepancy> discrepancies = new ArrayList<>();
    for (AccountType type : AccountType.values()) {
      long m = materialized.getOrDefault(type, 0L);
      long r = recomputed.getOrDefault(type, 0L);
      if (m != r) {
        discrepancies.add(new Discrepancy(type, m, r));
      }
    }
    return new TenantVerification(tenantId, discrepancies.isEmpty(), discrepancies);
  }

  private static Map<AccountType, Long> byType(List<AccountBalance> balances) {
    Map<AccountType, Long> byType = new EnumMap<>(AccountType.class);
    for (AccountBalance b : balances) {
      byType.merge(b.type(), b.balance().amountMinor(), Long::sum);
    }
    return byType;
  }
}
