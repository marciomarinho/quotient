package io.github.marciomarinho.quotient.ledger.application;

import io.github.marciomarinho.quotient.common.tenant.TenantId;
import io.github.marciomarinho.quotient.ledger.domain.AccountBalance;
import io.github.marciomarinho.quotient.ledger.domain.PostedTransaction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the ledger. Each method runs in a read-only transaction so the repository's {@code
 * SET LOCAL app.tenant_id} binding and the subsequent query share one connection and RLS scopes the
 * result to the tenant.
 */
@Service
public class LedgerQueryService {

  private final LedgerRepository repository;

  public LedgerQueryService(LedgerRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public List<AccountBalance> balances(TenantId tenantId) {
    return repository.balances(tenantId);
  }

  @Transactional(readOnly = true)
  public List<PostedTransaction> recentTransactions(TenantId tenantId, int limit) {
    return repository.recentTransactions(tenantId, Math.clamp(limit, 1, 500));
  }

  @Transactional(readOnly = true)
  public Optional<PostedTransaction> transaction(TenantId tenantId, UUID transactionId) {
    return repository.transaction(tenantId, transactionId);
  }
}
