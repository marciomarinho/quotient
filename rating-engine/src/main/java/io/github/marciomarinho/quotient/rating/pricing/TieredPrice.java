package io.github.marciomarinho.quotient.rating.pricing;

import io.github.marciomarinho.quotient.common.money.Currency;
import io.github.marciomarinho.quotient.common.money.Money;
import java.util.List;

/**
 * Graduated (tiered) pricing: each block of units is charged at the rate of the tier it falls into,
 * and the blocks are summed. For tiers 0–1M @ 30c/1k and 1M+ @ 20c/1k, a quantity of 1.5M costs 1M
 * at the first rate plus 0.5M at the second.
 */
public record TieredPrice(List<PriceTier> tiers) implements PricingModel {

  public TieredPrice(List<PriceTier> tiers) {
    this.tiers = Tiers.validated(tiers);
  }

  @Override
  public Money price(long quantity, Currency currency) {
    Money total = Money.zero(currency);
    long previousThreshold = 0;
    long remaining = quantity;
    for (PriceTier tier : tiers) {
      if (remaining <= 0) {
        break;
      }
      long upper = tier.isUnbounded() ? Long.MAX_VALUE : tier.upTo();
      long capacity = upper - previousThreshold;
      long unitsInTier = Math.min(remaining, capacity);
      total = total.plus(tier.cost(unitsInTier, currency));
      remaining -= unitsInTier;
      previousThreshold = upper;
    }
    return total;
  }
}
