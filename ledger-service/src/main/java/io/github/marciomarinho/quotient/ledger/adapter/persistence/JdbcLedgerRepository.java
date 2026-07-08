package io.github.marciomarinho.quotient.ledger.adapter.persistence;

import io.github.marciomarinho.quotient.common.money.Currency;
import io.github.marciomarinho.quotient.common.money.Money;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import io.github.marciomarinho.quotient.ledger.application.LedgerRepository;
import io.github.marciomarinho.quotient.ledger.application.PostOutcome;
import io.github.marciomarinho.quotient.ledger.domain.AccountBalance;
import io.github.marciomarinho.quotient.ledger.domain.AccountType;
import io.github.marciomarinho.quotient.ledger.domain.EntryDirection;
import io.github.marciomarinho.quotient.ledger.domain.PostedEntry;
import io.github.marciomarinho.quotient.ledger.domain.PostedTransaction;
import io.github.marciomarinho.quotient.ledger.domain.PostingLine;
import io.github.marciomarinho.quotient.ledger.domain.PostingPlan;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * JDBC adapter for {@link LedgerRepository}, using {@link JdbcClient} over the least-privilege
 * {@code quotient_app} connection.
 *
 * <p>Every tenant-scoped statement runs after {@link #bindTenant} has set {@code app.tenant_id} for
 * the transaction, so PostgreSQL RLS silently scopes reads and writes to the current tenant.
 * Posting is idempotent because the transaction row is inserted with {@code ON CONFLICT DO NOTHING}
 * on its deterministic id — a replay affects zero rows and short-circuits.
 */
@Repository
public class JdbcLedgerRepository implements LedgerRepository {

  private final JdbcClient jdbc;

  public JdbcLedgerRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void bindTenant(TenantId tenantId) {
    // set_config(..., is_local=true) scopes the setting to the current tx.
    jdbc.sql("SELECT set_config('app.tenant_id', ?, true)")
        .param(tenantId.asString())
        .query(String.class)
        .single();
  }

  @Override
  public PostOutcome post(PostingPlan plan) {
    bindTenant(plan.tenantId());

    int inserted =
        jdbc.sql(
                "INSERT INTO ledger_transaction (id, tenant_id, description, source_charge_id) "
                    + "VALUES (?, ?, ?, ?) ON CONFLICT (id) DO NOTHING")
            .param(plan.transactionId())
            .param(plan.tenantId().value())
            .param(plan.description())
            .param(plan.sourceChargeId())
            .update();

    if (inserted == 0) {
      return PostOutcome.DUPLICATE;
    }

    Map<AccountType, UUID> accountIds = new EnumMap<>(AccountType.class);
    for (PostingLine line : plan.lines()) {
      accountIds.computeIfAbsent(
          line.accountType(), t -> ensureAccount(plan.tenantId(), t, plan.currency()));
    }

    for (PostingLine line : plan.lines()) {
      UUID accountId = accountIds.get(line.accountType());
      jdbc.sql(
              "INSERT INTO ledger_entry "
                  + "(transaction_id, tenant_id, account_id, direction, amount_minor, currency) "
                  + "VALUES (?, ?, ?, ?, ?, ?)")
          .param(plan.transactionId())
          .param(plan.tenantId().value())
          .param(accountId)
          .param(line.direction().name())
          .param(line.amount().amountMinor())
          .param(plan.currency().name())
          .update();

      long delta = signedDelta(line);
      jdbc.sql(
              "INSERT INTO account_balance (account_id, tenant_id, balance_minor, currency) "
                  + "VALUES (?, ?, ?, ?) "
                  + "ON CONFLICT (account_id) DO UPDATE "
                  + "SET balance_minor = account_balance.balance_minor + EXCLUDED.balance_minor, "
                  + "    updated_at = now()")
          .param(accountId)
          .param(plan.tenantId().value())
          .param(delta)
          .param(plan.currency().name())
          .update();
    }

    return PostOutcome.POSTED;
  }

  @Override
  public void recordBilledCharge(io.github.marciomarinho.quotient.common.event.Charge charge) {
    jdbc.sql(
            "INSERT INTO billed_charge (charge_id, tenant_id, meter_code, window_start, "
                + "window_end, quantity_billed, amount_minor, currency, plan_version) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (charge_id) DO NOTHING")
        .param(charge.chargeId())
        .param(charge.tenantId().value())
        .param(charge.meterCode())
        .param(
            java.time.OffsetDateTime.ofInstant(charge.window().start(), java.time.ZoneOffset.UTC))
        .param(java.time.OffsetDateTime.ofInstant(charge.window().end(), java.time.ZoneOffset.UTC))
        .param(charge.quantityBilled())
        .param(charge.amount().amountMinor())
        .param(charge.amount().currency().name())
        .param(charge.planVersion())
        .update();
  }

  /** Balances are debit-positive: DEBIT adds, CREDIT subtracts. */
  private static long signedDelta(PostingLine line) {
    long amount = line.amount().amountMinor();
    return line.direction() == EntryDirection.DEBIT ? amount : -amount;
  }

  private UUID ensureAccount(TenantId tenantId, AccountType type, Currency currency) {
    jdbc.sql(
            "INSERT INTO ledger_account (id, tenant_id, type, currency) "
                + "VALUES (?, ?, ?, ?) ON CONFLICT (tenant_id, type, currency) DO NOTHING")
        .param(UUID.randomUUID())
        .param(tenantId.value())
        .param(type.name())
        .param(currency.name())
        .update();

    return jdbc.sql(
            "SELECT id FROM ledger_account WHERE tenant_id = ? AND type = ? AND currency = ?")
        .param(tenantId.value())
        .param(type.name())
        .param(currency.name())
        .query(UUID.class)
        .single();
  }

  @Override
  public List<AccountBalance> balances(TenantId tenantId) {
    bindTenant(tenantId);
    return jdbc.sql(
            "SELECT ab.account_id, la.type, ab.balance_minor, ab.currency "
                + "FROM account_balance ab JOIN ledger_account la ON la.id = ab.account_id "
                + "ORDER BY la.type")
        .query(
            (rs, n) ->
                mapBalance(
                    rs.getObject("account_id", UUID.class),
                    rs.getString("type"),
                    rs.getLong("balance_minor"),
                    rs.getString("currency")))
        .list();
  }

  @Override
  public List<AccountBalance> recomputedBalances(TenantId tenantId) {
    bindTenant(tenantId);
    return jdbc.sql(
            "SELECT e.account_id, la.type, la.currency, "
                + "SUM(CASE WHEN e.direction = 'DEBIT' THEN e.amount_minor "
                + "         ELSE -e.amount_minor END) AS bal "
                + "FROM ledger_entry e JOIN ledger_account la ON la.id = e.account_id "
                + "GROUP BY e.account_id, la.type, la.currency ORDER BY la.type")
        .query(
            (rs, n) ->
                mapBalance(
                    rs.getObject("account_id", UUID.class),
                    rs.getString("type"),
                    rs.getLong("bal"),
                    rs.getString("currency")))
        .list();
  }

  private static AccountBalance mapBalance(
      UUID accountId, String type, long balanceMinor, String currency) {
    return new AccountBalance(
        accountId,
        AccountType.valueOf(type),
        Money.ofMinor(balanceMinor, Currency.valueOf(currency)));
  }

  @Override
  public List<PostedTransaction> recentTransactions(TenantId tenantId, int limit) {
    bindTenant(tenantId);
    List<PostedTransaction> transactions = new ArrayList<>();
    List<TxHeader> headers =
        jdbc.sql(
                "SELECT id, description, created_at FROM ledger_transaction "
                    + "ORDER BY created_at DESC, id LIMIT ?")
            .param(limit)
            .query(
                (rs, n) ->
                    new TxHeader(
                        rs.getObject("id", UUID.class),
                        rs.getString("description"),
                        rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant()))
            .list();
    for (TxHeader h : headers) {
      transactions.add(
          new PostedTransaction(h.id(), h.description(), h.createdAt(), entries(h.id())));
    }
    return transactions;
  }

  @Override
  public Optional<PostedTransaction> transaction(TenantId tenantId, UUID transactionId) {
    bindTenant(tenantId);
    Optional<TxHeader> header =
        jdbc.sql("SELECT id, description, created_at FROM ledger_transaction WHERE id = ?")
            .param(transactionId)
            .query(
                (rs, n) ->
                    new TxHeader(
                        rs.getObject("id", UUID.class),
                        rs.getString("description"),
                        rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant()))
            .optional();
    return header.map(
        h -> new PostedTransaction(h.id(), h.description(), h.createdAt(), entries(h.id())));
  }

  private List<PostedEntry> entries(UUID transactionId) {
    return jdbc.sql(
            "SELECT la.type, e.direction, e.amount_minor, e.currency "
                + "FROM ledger_entry e JOIN ledger_account la ON la.id = e.account_id "
                + "WHERE e.transaction_id = ? ORDER BY e.id")
        .param(transactionId)
        .query(
            (rs, n) ->
                new PostedEntry(
                    AccountType.valueOf(rs.getString("type")),
                    EntryDirection.valueOf(rs.getString("direction")),
                    Money.ofMinor(
                        rs.getLong("amount_minor"), Currency.valueOf(rs.getString("currency")))))
        .list();
  }

  @Override
  public List<TenantId> allTenantIds() {
    return jdbc.sql("SELECT id FROM tenant ORDER BY id")
        .query((rs, n) -> TenantId.of(rs.getObject("id", UUID.class)))
        .list();
  }

  private record TxHeader(UUID id, String description, java.time.Instant createdAt) {}
}
