package io.github.marciomarinho.quotient.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.marciomarinho.quotient.ledger.application.LedgerPostingService;
import io.github.marciomarinho.quotient.ledger.application.LedgerQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the multi-tenant isolation and append-only guarantees at the database level, exercised
 * through the same least-privilege {@code quotient_app} connection the app uses in production.
 */
class LedgerRlsIntegrationTest extends AbstractLedgerIntegrationTest {

  @Autowired LedgerPostingService postingService;
  @Autowired LedgerQueryService queryService;
  @Autowired JdbcClient jdbc;
  @Autowired TransactionTemplate tx;

  @BeforeEach
  void seedTwoTenants() {
    postingService.post(charge(ACME, 1000, "llm.tokens.input.rls-acme"));
    postingService.post(charge(GLOBEX, 2000, "llm.tokens.input.rls-globex"));
  }

  @Test
  void aTenantSeesOnlyItsOwnBalances() {
    // Acme's receivable reflects only Acme's charge (1000 + 10% = 1100), not Globex's.
    long acmeReceivable =
        queryService.balances(ACME).stream()
            .filter(b -> b.type().name().equals("RECEIVABLE"))
            .mapToLong(b -> b.balance().amountMinor())
            .sum();
    assertThat(acmeReceivable).isEqualTo(1100L);
  }

  @Test
  void craftedCrossTenantQuery_returnsZeroRowsUnderRls() {
    // Bind the session to Acme, then deliberately query FOR Globex's rows.
    // RLS must still restrict the result to Acme, so the count is zero.
    Long globexRowsVisibleToAcme =
        tx.execute(
            status -> {
              bind(ACME.asString());
              return jdbc.sql("SELECT count(*) FROM ledger_entry WHERE tenant_id = ?::uuid")
                  .param(GLOBEX.asString())
                  .query(Long.class)
                  .single();
            });

    assertThat(globexRowsVisibleToAcme)
        .as("RLS must hide another tenant's rows even from a query that explicitly names them")
        .isZero();
  }

  @Test
  void ledgerEntriesAreAppendOnly_updateIsDenied() {
    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    status -> {
                      bind(ACME.asString());
                      jdbc.sql("UPDATE ledger_entry SET amount_minor = 0").update();
                    }))
        .as("quotient_app has no UPDATE privilege on ledger_entry")
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void ledgerEntriesAreAppendOnly_deleteIsDenied() {
    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    status -> {
                      bind(ACME.asString());
                      jdbc.sql("DELETE FROM ledger_entry").update();
                    }))
        .as("quotient_app has no DELETE privilege on ledger_entry")
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void unbalancedTransaction_isRejectedByTheDeferredTrigger() {
    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    status -> {
                      bind(ACME.asString());
                      var txId = java.util.UUID.randomUUID();
                      jdbc.sql(
                              "INSERT INTO ledger_transaction (id, tenant_id, description) "
                                  + "VALUES (?, ?::uuid, 'deliberately unbalanced')")
                          .param(txId)
                          .param(ACME.asString())
                          .update();
                      var receivable = accountId("RECEIVABLE");
                      var revenue = accountId("REVENUE");
                      // Two entries (a real double-entry shape) but debits != credits.
                      insertEntry(txId, receivable, "DEBIT", 5000);
                      insertEntry(txId, revenue, "CREDIT", 4000);
                      // Deferred trigger fires at commit and rejects: 5000 != 4000.
                    }))
        .as("the deferred constraint trigger must reject an unbalanced transaction at commit")
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("unbalanced");
  }

  private void bind(String tenantId) {
    jdbc.sql("SELECT set_config('app.tenant_id', ?, true)")
        .param(tenantId)
        .query(String.class)
        .single();
  }

  private java.util.UUID accountId(String type) {
    return jdbc.sql("SELECT id FROM ledger_account WHERE tenant_id = ?::uuid AND type = ?")
        .param(ACME.asString())
        .param(type)
        .query(java.util.UUID.class)
        .single();
  }

  private void insertEntry(
      java.util.UUID txId, java.util.UUID accountId, String direction, long amountMinor) {
    jdbc.sql(
            "INSERT INTO ledger_entry "
                + "(transaction_id, tenant_id, account_id, direction, amount_minor, currency) "
                + "VALUES (?, ?::uuid, ?, ?, ?, 'AUD')")
        .param(txId)
        .param(ACME.asString())
        .param(accountId)
        .param(direction)
        .param(amountMinor)
        .update();
  }
}
