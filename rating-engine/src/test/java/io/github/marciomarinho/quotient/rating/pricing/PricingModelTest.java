package io.github.marciomarinho.quotient.rating.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.marciomarinho.quotient.common.money.Currency;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Worked examples for the three pricing schemes. Amounts are AUD minor units. */
class PricingModelTest {

  private static long price(PricingModel model, long quantity) {
    return model.price(quantity, Currency.AUD).amountMinor();
  }

  // 0..1,000,000 @ 30c/1k, then @ 20c/1k.
  private static final List<PriceTier> LADDER =
      List.of(new PriceTier(1_000_000L, 30, 1_000), new PriceTier(null, 20, 1_000));

  @Test
  void flatChargesOneRateForEveryUnit() {
    FlatPrice flat = new FlatPrice(new PriceTier(null, 5, 1));

    assertThat(price(flat, 100)).as("100 requests @ 5c").isEqualTo(500L);
  }

  @Test
  void tieredChargesEachBlockAtItsOwnRate() {
    TieredPrice tiered = new TieredPrice(LADDER);

    // Within the first tier: 500k/1k * 30 = 15,000.
    assertThat(price(tiered, 500_000)).isEqualTo(15_000L);
    // Across the boundary: 1M @ 30/1k (30,000) + 0.5M @ 20/1k (10,000) = 40,000.
    assertThat(price(tiered, 1_500_000)).isEqualTo(40_000L);
  }

  @Test
  void volumeChargesTheWholeQuantityAtTheSelectedTierRate() {
    VolumePrice volume = new VolumePrice(LADDER);

    // Falls in first tier: all 500k @ 30/1k = 15,000.
    assertThat(price(volume, 500_000)).isEqualTo(15_000L);
    // Falls in the top tier: ALL 1.5M repriced @ 20/1k = 30,000 (cheaper than tiered).
    assertThat(price(volume, 1_500_000)).isEqualTo(30_000L);
  }

  @Test
  void tieredNeverCostsLessThanVolumeForTheSameLadder() {
    // A volume discount can only lower the bill relative to graduated pricing.
    assertThat(price(new TieredPrice(LADDER), 1_500_000))
        .isGreaterThanOrEqualTo(price(new VolumePrice(LADDER), 1_500_000));
  }

  @Test
  void rejectsLadderWhoseLastTierIsBounded() {
    assertThatThrownBy(() -> new TieredPrice(List.of(new PriceTier(100L, 1, 1))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unbounded");
  }

  @Test
  void rejectsNonIncreasingThresholds() {
    assertThatThrownBy(
            () ->
                new TieredPrice(
                    List.of(
                        new PriceTier(100L, 1, 1),
                        new PriceTier(100L, 1, 1),
                        new PriceTier(null, 1, 1))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("increase");
  }
}
