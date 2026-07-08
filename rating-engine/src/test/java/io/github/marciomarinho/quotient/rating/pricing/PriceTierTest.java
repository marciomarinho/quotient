package io.github.marciomarinho.quotient.rating.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.marciomarinho.quotient.common.money.Currency;
import io.github.marciomarinho.quotient.common.money.Money;
import org.junit.jupiter.api.Test;

class PriceTierTest {

  private static long costMinor(long units, long unitPriceMinor, long perUnits) {
    return new PriceTier(null, unitPriceMinor, perUnits).cost(units, Currency.AUD).amountMinor();
  }

  @Test
  void perThousandPricingIsExactWhenDivisible() {
    // 30 cents per 1,000 units, 1,500 units -> 45 cents.
    assertThat(costMinor(1_500, 30, 1_000)).isEqualTo(45L);
  }

  @Test
  void roundsHalfEvenDownAtExactHalf() {
    // 1 minor per 2 units, 1 unit -> 0.5 -> rounds to even (0).
    assertThat(costMinor(1, 1, 2)).isEqualTo(0L);
  }

  @Test
  void roundsHalfEvenUpAtExactHalf() {
    // 1 minor per 2 units, 3 units -> 1.5 -> rounds to even (2).
    assertThat(costMinor(3, 1, 2)).isEqualTo(2L);
  }

  @Test
  void zeroUnitsCostNothing() {
    assertThat(new PriceTier(null, 30, 1_000).cost(0, Currency.AUD))
        .isEqualTo(Money.zero(Currency.AUD));
  }
}
