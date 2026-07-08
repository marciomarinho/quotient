package io.github.marciomarinho.quotient.rating.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.marciomarinho.quotient.common.money.Currency;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;

/** Property-based invariants of the pricing models. */
class PricingPropertiesTest {

  private static final List<PriceTier> LADDER =
      List.of(new PriceTier(1_000_000L, 30, 1_000), new PriceTier(null, 20, 1_000));
  private static final TieredPrice TIERED = new TieredPrice(LADDER);
  private static final VolumePrice VOLUME = new VolumePrice(LADDER);

  @Property
  void ratingIsDeterministic(@ForAll @LongRange(min = 0, max = 5_000_000) long quantity) {
    // Re-rating the same quantity must produce an identical amount.
    assertThat(TIERED.price(quantity, Currency.AUD))
        .isEqualTo(TIERED.price(quantity, Currency.AUD));
  }

  @Property
  void tieredIsMonotonicNonDecreasing(
      @ForAll @LongRange(min = 0, max = 5_000_000) long a,
      @ForAll @LongRange(min = 0, max = 5_000_000) long b) {
    long lower = Math.min(a, b);
    long higher = Math.max(a, b);

    assertThat(TIERED.price(higher, Currency.AUD).amountMinor())
        .isGreaterThanOrEqualTo(TIERED.price(lower, Currency.AUD).amountMinor());
  }

  @Property
  void tieredIsNeverCheaperThanVolumeForTheSameLadder(
      @ForAll @LongRange(min = 0, max = 5_000_000) long quantity) {
    assertThat(TIERED.price(quantity, Currency.AUD).amountMinor())
        .isGreaterThanOrEqualTo(VOLUME.price(quantity, Currency.AUD).amountMinor());
  }
}
