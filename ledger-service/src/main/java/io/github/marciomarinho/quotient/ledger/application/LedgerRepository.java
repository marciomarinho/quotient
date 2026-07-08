package io.github.marciomarinho.quotient.ledger.application;

import io.github.marciomarinho.quotient.common.tenant.TenantId;
import io.github.marciomarinho.quotient.ledger.domain.AccountBalance;
import io.github.marciomarinho.quotient.ledger.domain.PostedTransaction;
import io.github.marciomarinho.quotient.ledger.domain.PostingPlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The persistence port for the ledger. This is the boundary between the application/domain and the
 * database adapter: it speaks in domain types ({@link PostingPlan}, {@link AccountBalance}, {@link
 * PostedTransaction}) and knows nothing about JDBC or SQL.
 *
 * <p>Every method operates within the caller's transaction and the caller's tenant context.
 * Implementations must bind {@code app.tenant_id} for the current transaction (via {@link
 * #bindTenant(TenantId)}) before touching tenant-scoped tables so PostgreSQL RLS applies.
 */
public interface LedgerRepository {

  /** Bind the tenant for the current transaction so RLS scopes every subsequent statement. */
  void bindTenant(TenantId tenantId);

  /**
   * Record a balanced posting. Idempotent on {@link PostingPlan#transactionId()}: a replay of the
   * same charge returns {@link PostOutcome#DUPLICATE} and changes nothing.
   */
  PostOutcome post(PostingPlan plan);

  /** Materialized balances for the bound tenant's accounts. */
  List<AccountBalance> balances(TenantId tenantId);

  /**
   * Balances recomputed from raw entries for the bound tenant — used by the verifier to prove the
   * materialized balances are correct.
   */
  List<AccountBalance> recomputedBalances(TenantId tenantId);

  /** The most recent transactions for the bound tenant, newest first. */
  List<PostedTransaction> recentTransactions(TenantId tenantId, int limit);

  /** A single transaction by id, scoped to the bound tenant. */
  Optional<PostedTransaction> transaction(TenantId tenantId, UUID transactionId);

  /** All tenant ids known to the ledger (not tenant-scoped; used by the verifier to iterate). */
  List<TenantId> allTenantIds();
}
