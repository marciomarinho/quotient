package io.github.marciomarinho.quotient.rating.pricing;

import io.github.marciomarinho.quotient.common.money.Currency;
import io.github.marciomarinho.quotient.common.money.Money;
import java.util.List;

/**
 * Volume pricing: the total quantity selects a single tier, whose rate then applies to <em>all</em>
 * units. Unlike graduated pricing, crossing into a cheaper tier reprices everything at the cheaper
 * rate — a volume discount.
 */
public record VolumePrice(List<PriceTier> tiers) implements PricingModel {

  public VolumePrice(List<PriceTier> tiers) {
    this.tiers = Tiers.validated(tiers);
  }

  @Override
  public Money price(long quantity, Currency currency) {
    for (PriceTier tier : tiers) {
      if (tier.isUnbounded() || quantity <= tier.upTo()) {
        return tier.cost(quantity, currency);
      }
    }
    // Unreachable: the last tier is always unbounded (enforced by Tiers.validated).
    throw new IllegalStateException("no tier matched quantity " + quantity);
  }
}
