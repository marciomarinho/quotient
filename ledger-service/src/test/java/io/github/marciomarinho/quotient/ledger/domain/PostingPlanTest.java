package io.github.marciomarinho.quotient.ledger.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.marciomarinho.quotient.common.money.Currency;
import io.github.marciomarinho.quotient.common.money.Money;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The application-layer balance invariant: an unbalanced plan cannot exist. */
class PostingPlanTest {

  private static final TenantId TENANT =
      TenantId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
  private static final UUID TX = UUID.randomUUID();

  @Test
  void acceptsABalancedPosting() {
    assertThatCode(
            () ->
                new PostingPlan(
                    TX,
                    TENANT,
                    "ok",
                    TX,
                    Currency.AUD,
                    List.of(
                        PostingLine.debit(
                            AccountType.RECEIVABLE, Money.ofMinor(1100, Currency.AUD)),
                        PostingLine.credit(AccountType.REVENUE, Money.ofMinor(1000, Currency.AUD)),
                        PostingLine.credit(AccountType.TAX, Money.ofMinor(100, Currency.AUD)))))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsUnbalancedDebitsAndCredits() {
    assertThatThrownBy(
            () ->
                new PostingPlan(
                    TX,
                    TENANT,
                    "bad",
                    TX,
                    Currency.AUD,
                    List.of(
                        PostingLine.debit(
                            AccountType.RECEIVABLE, Money.ofMinor(1000, Currency.AUD)),
                        PostingLine.credit(AccountType.REVENUE, Money.ofMinor(999, Currency.AUD)))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unbalanced");
  }

  @Test
  void rejectsSingleLinePosting() {
    assertThatThrownBy(
            () ->
                new PostingPlan(
                    TX,
                    TENANT,
                    "bad",
                    TX,
                    Currency.AUD,
                    List.of(
                        PostingLine.debit(
                            AccountType.RECEIVABLE, Money.ofMinor(1000, Currency.AUD)))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(">= 2 lines");
  }
}
