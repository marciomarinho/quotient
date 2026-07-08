package io.github.marciomarinho.quotient.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.marciomarinho.quotient.common.money.Currency;
import io.github.marciomarinho.quotient.common.money.Money;
import org.junit.jupiter.api.Test;

class GstPolicyTest {

  private final GstPolicy gst = GstPolicy.AUSTRALIA_GST;

  @Test
  void tenPercentOfAWholeAmount() {
    assertThat(gst.on(Money.ofMinor(1000, Currency.AUD)))
        .isEqualTo(Money.ofMinor(100, Currency.AUD));
  }

  @Test
  void roundsHalfEvenDown_atExactHalf() {
    // 10% of 5 minor units = 0.5, HALF_EVEN rounds to the even neighbour: 0.
    assertThat(gst.on(Money.ofMinor(5, Currency.AUD)))
        .as("0.5 rounds to even (0)")
        .isEqualTo(Money.ofMinor(0, Currency.AUD));
  }

  @Test
  void roundsHalfEvenUp_atExactHalf() {
    // 10% of 15 minor units = 1.5, HALF_EVEN rounds to the even neighbour: 2.
    assertThat(gst.on(Money.ofMinor(15, Currency.AUD)))
        .as("1.5 rounds to even (2)")
        .isEqualTo(Money.ofMinor(2, Currency.AUD));
  }

  @Test
  void zeroRevenueHasZeroTax() {
    assertThat(gst.on(Money.zero(Currency.AUD))).isEqualTo(Money.zero(Currency.AUD));
  }
}
