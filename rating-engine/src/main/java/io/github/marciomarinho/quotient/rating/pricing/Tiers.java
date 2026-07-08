package io.github.marciomarinho.quotient.rating.pricing;

import java.util.List;

/** Shared validation for ordered tier lists used by tiered and volume pricing. */
final class Tiers {

  private Tiers() {}

  /**
   * Validate that {@code tiers} is a well-formed ascending ladder: non-empty, strictly increasing
   * {@code upTo} thresholds, and exactly one unbounded tier which must be last (so no quantity is
   * ever left unpriced).
   */
  static List<PriceTier> validated(List<PriceTier> tiers) {
    if (tiers == null || tiers.isEmpty()) {
      throw new IllegalArgumentException("at least one tier is required");
    }
    List<PriceTier> copy = List.copyOf(tiers);
    long previous = -1;
    for (int i = 0; i < copy.size(); i++) {
      PriceTier tier = copy.get(i);
      boolean last = i == copy.size() - 1;
      if (tier.isUnbounded() != last) {
        throw new IllegalArgumentException(
            "exactly one unbounded tier is allowed and it must be last");
      }
      if (!tier.isUnbounded()) {
        if (tier.upTo() <= previous) {
          throw new IllegalArgumentException("tier upTo thresholds must strictly increase");
        }
        previous = tier.upTo();
      }
    }
    return copy;
  }
}
