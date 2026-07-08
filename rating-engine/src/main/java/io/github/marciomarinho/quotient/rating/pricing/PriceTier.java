package io.github.marciomarinho.quotient.rating.pricing;

import io.github.marciomarinho.quotient.common.money.Currency;
import io.github.marciomarinho.quotient.common.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One pricing tier: a unit price that applies up to a cumulative quantity threshold.
 *
 * <p>The price is expressed as {@code unitPriceMinor} minor units per {@code perUnits} units — e.g.
 * 30 cents per 1,000 tokens is {@code unitPriceMinor=30, perUnits=1000}. This is the one place
 * fractional per-unit pricing arises, so the cost is computed with {@link BigDecimal} and {@link
 * RoundingMode#HALF_EVEN}, then converted straight back to {@code long} minor units — never {@code
 * double}.
 *
 * @param upTo cumulative quantity this tier extends to; {@code null} means unbounded (the top tier)
 * @param unitPriceMinor price in minor units per {@code perUnits} units
 * @param perUnits the pricing quantum (e.g. 1000 for per-1k-token pricing); must be >= 1
 */
public record PriceTier(Long upTo, long unitPriceMinor, long perUnits) {

  public PriceTier {
    if (perUnits < 1) {
      throw new IllegalArgumentException("perUnits must be >= 1, was " + perUnits);
    }
    if (unitPriceMinor < 0) {
      throw new IllegalArgumentException("unitPriceMinor must be >= 0, was " + unitPriceMinor);
    }
    if (upTo != null && upTo < 0) {
      throw new IllegalArgumentException("upTo must be >= 0 or null, was " + upTo);
    }
  }

  /** The cost of {@code units} at this tier's rate, rounded HALF_EVEN to minor units. */
  public Money cost(long units, Currency currency) {
    if (units <= 0) {
      return Money.zero(currency);
    }
    long minor =
        BigDecimal.valueOf(units)
            .multiply(BigDecimal.valueOf(unitPriceMinor))
            .divide(BigDecimal.valueOf(perUnits), 0, RoundingMode.HALF_EVEN)
            .longValueExact();
    return Money.ofMinor(minor, currency);
  }

  /** True if this is the unbounded (top) tier. */
  public boolean isUnbounded() {
    return upTo == null;
  }
}
