package io.github.marciomarinho.quotient.common.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

  @Test
  void ofMajor_convertsToMinorUnitsUsingCurrencyScale() {
    assertThat(Money.ofMajor(12, Currency.AUD).amountMinor())
        .as("12 AUD should be 1200 cents")
        .isEqualTo(1200L);
  }

  @Test
  void plus_addsAmountsInSameCurrency() {
    Money sum = Money.ofMinor(1200, Currency.AUD).plus(Money.ofMinor(350, Currency.AUD));

    assertThat(sum.amountMinor()).isEqualTo(1550L);
  }

  @Test
  void minus_subtractsAmounts() {
    Money diff = Money.ofMinor(1000, Currency.AUD).minus(Money.ofMinor(1500, Currency.AUD));

    assertThat(diff.amountMinor()).as("result may be negative").isEqualTo(-500L);
  }

  @Test
  void times_scalesByWholeFactor() {
    Money total = Money.ofMinor(7, Currency.AUD).times(1000);

    assertThat(total.amountMinor()).isEqualTo(7000L);
  }

  @Test
  void times_overflowThrowsRatherThanWrapping() {
    Money huge = Money.ofMinor(Long.MAX_VALUE, Currency.AUD);

    assertThatThrownBy(() -> huge.times(2))
        .as("exact arithmetic must overflow loudly, never silently wrap")
        .isInstanceOf(ArithmeticException.class);
  }

  @Test
  void zero_isZeroAndSignPredicatesAgree() {
    Money zero = Money.zero(Currency.AUD);

    assertThat(zero.isZero()).isTrue();
    assertThat(zero.isPositive()).isFalse();
    assertThat(zero.isNegative()).isFalse();
  }

  @Test
  void toString_rendersSignedMajorAndMinor() {
    assertThat(Money.ofMinor(1234, Currency.AUD).toString()).isEqualTo("AUD 12.34");
    assertThat(Money.ofMinor(-5, Currency.AUD).toString()).isEqualTo("AUD -0.05");
    assertThat(Money.ofMinor(0, Currency.AUD).toString()).isEqualTo("AUD 0.00");
  }

  @Test
  void equality_isByValue() {
    assertThat(Money.ofMinor(500, Currency.AUD)).isEqualTo(Money.ofMinor(500, Currency.AUD));
  }
}
