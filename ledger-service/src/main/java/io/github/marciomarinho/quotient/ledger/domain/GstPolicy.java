package io.github.marciomarinho.quotient.ledger.domain;

import io.github.marciomarinho.quotient.common.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Computes the tax portion of a charge. Australian GST is a flat 10% of the net (ex-tax) revenue.
 *
 * <p>This is the one spot in the ledger where a percentage is applied, so — like the rating
 * engine's unit-price × quantity step — it uses {@link BigDecimal} with an explicit {@link
 * RoundingMode#HALF_EVEN} and converts straight back to {@code long} minor units. No {@code double}
 * anywhere.
 */
public final class GstPolicy {

  /** The standard Australian GST rate, 10%. */
  public static final GstPolicy AUSTRALIA_GST = new GstPolicy(new BigDecimal("0.10"));

  private final BigDecimal rate;

  public GstPolicy(BigDecimal rate) {
    this.rate = Objects.requireNonNull(rate, "rate");
  }

  /** The tax on a net revenue amount, rounded HALF_EVEN to whole minor units. */
  public Money on(Money netRevenue) {
    Objects.requireNonNull(netRevenue, "netRevenue");
    long taxMinor =
        BigDecimal.valueOf(netRevenue.amountMinor())
            .multiply(rate)
            .setScale(0, RoundingMode.HALF_EVEN)
            .longValueExact();
    return Money.ofMinor(taxMinor, netRevenue.currency());
  }
}
