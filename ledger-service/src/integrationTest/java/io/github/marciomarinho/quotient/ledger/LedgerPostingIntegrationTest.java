package io.github.marciomarinho.quotient.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.marciomarinho.quotient.common.event.Charge;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import io.github.marciomarinho.quotient.ledger.application.LedgerPostingService;
import io.github.marciomarinho.quotient.ledger.application.LedgerQueryService;
import io.github.marciomarinho.quotient.ledger.application.LedgerVerifier;
import io.github.marciomarinho.quotient.ledger.application.PostOutcome;
import io.github.marciomarinho.quotient.ledger.domain.AccountBalance;
import io.github.marciomarinho.quotient.ledger.domain.AccountType;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class LedgerPostingIntegrationTest extends AbstractLedgerIntegrationTest {

  @Autowired LedgerPostingService postingService;
  @Autowired LedgerQueryService queryService;
  @Autowired LedgerVerifier verifier;

  @Test
  void postingACharge_recordsBalancedDoubleEntry() {
    Charge charge = charge(ACME, 1000, "llm.tokens.input.post-balanced");

    PostOutcome outcome = postingService.post(charge);

    assertThat(outcome).isEqualTo(PostOutcome.POSTED);
    Map<AccountType, Long> balances = balancesByType(ACME);
    // Debit-positive convention: RECEIVABLE positive, REVENUE/TAX negative.
    assertThat(balances.get(AccountType.RECEIVABLE)).isEqualTo(1100L);
    assertThat(balances.get(AccountType.REVENUE)).isEqualTo(-1000L);
    assertThat(balances.get(AccountType.TAX)).isEqualTo(-100L);
    // Ledger stays internally consistent: total debit-positive balance is zero.
    assertThat(balances.values().stream().mapToLong(Long::longValue).sum()).isZero();
  }

  @Test
  void repostingTheSameCharge_isIdempotent() {
    Charge charge = charge(ACME, 500, "llm.tokens.input.idempotent");

    assertThat(postingService.post(charge)).isEqualTo(PostOutcome.POSTED);
    Map<AccountType, Long> afterFirst = balancesByType(ACME);

    // Replay: same deterministic charge id.
    assertThat(postingService.post(charge)).isEqualTo(PostOutcome.DUPLICATE);
    Map<AccountType, Long> afterReplay = balancesByType(ACME);

    assertThat(afterReplay).as("a replayed charge must not move balances").isEqualTo(afterFirst);
  }

  @Test
  void verifier_confirmsMaterializedBalancesMatchRecomputed() {
    postingService.post(charge(ACME, 777, "llm.tokens.input.verify-a"));
    postingService.post(charge(GLOBEX, 250, "llm.tokens.input.verify-b"));

    assertThat(verifier.verifyAll().allMatch())
        .as("recomputed balances must equal materialized balances for every tenant")
        .isTrue();
  }

  private Map<AccountType, Long> balancesByType(TenantId tenant) {
    return queryService.balances(tenant).stream()
        .collect(Collectors.toMap(AccountBalance::type, b -> b.balance().amountMinor(), Long::sum));
  }
}
