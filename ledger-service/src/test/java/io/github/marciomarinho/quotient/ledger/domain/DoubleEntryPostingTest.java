package io.github.marciomarinho.quotient.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.marciomarinho.quotient.common.event.BillingWindow;
import io.github.marciomarinho.quotient.common.event.Charge;
import io.github.marciomarinho.quotient.common.money.Currency;
import io.github.marciomarinho.quotient.common.money.Money;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import java.time.Instant;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DoubleEntryPostingTest {

  private final DoubleEntryPosting posting = new DoubleEntryPosting(GstPolicy.AUSTRALIA_GST);

  private static Charge chargeOf(long revenueMinor) {
    return Charge.of(
        TenantId.of(UUID.fromString("11111111-1111-1111-1111-111111111111")),
        "llm.tokens.input",
        new BillingWindow(
            Instant.parse("2026-07-08T00:00:00Z"), Instant.parse("2026-07-08T00:01:00Z")),
        new TreeMap<>(),
        4200,
        Money.ofMinor(revenueMinor, Currency.AUD),
        7);
  }

  @Test
  void producesBalancedThreeLinePosting() {
    PostingPlan plan = posting.forCharge(chargeOf(1000));

    // Constructing the plan already asserts balance; assert the shape explicitly.
    assertThat(plan.lines()).hasSize(3);
    assertThat(lineAmount(plan, AccountType.REVENUE)).isEqualTo(1000L);
    assertThat(lineAmount(plan, AccountType.TAX)).isEqualTo(100L);
    assertThat(lineAmount(plan, AccountType.RECEIVABLE))
        .as("receivable = revenue + tax")
        .isEqualTo(1100L);
  }

  @Test
  void receivableIsDebit_revenueAndTaxAreCredits() {
    PostingPlan plan = posting.forCharge(chargeOf(1000));

    assertThat(direction(plan, AccountType.RECEIVABLE)).isEqualTo(EntryDirection.DEBIT);
    assertThat(direction(plan, AccountType.REVENUE)).isEqualTo(EntryDirection.CREDIT);
    assertThat(direction(plan, AccountType.TAX)).isEqualTo(EntryDirection.CREDIT);
  }

  @Test
  void transactionIdIsTheDeterministicChargeId() {
    Charge charge = chargeOf(1000);
    PostingPlan plan = posting.forCharge(charge);

    assertThat(plan.transactionId()).isEqualTo(charge.chargeId());
  }

  @Test
  void isDeterministic_sameChargeYieldsSamePlan() {
    Charge charge = chargeOf(1234);

    assertThat(posting.forCharge(charge)).isEqualTo(posting.forCharge(charge));
  }

  private static long lineAmount(PostingPlan plan, AccountType type) {
    return plan.lines().stream()
        .filter(l -> l.accountType() == type)
        .findFirst()
        .orElseThrow()
        .amount()
        .amountMinor();
  }

  private static EntryDirection direction(PostingPlan plan, AccountType type) {
    return plan.lines().stream()
        .filter(l -> l.accountType() == type)
        .findFirst()
        .orElseThrow()
        .direction();
  }
}
