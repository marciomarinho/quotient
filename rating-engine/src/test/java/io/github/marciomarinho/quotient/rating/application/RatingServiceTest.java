package io.github.marciomarinho.quotient.rating.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.marciomarinho.quotient.common.event.BillingWindow;
import io.github.marciomarinho.quotient.common.event.Charge;
import io.github.marciomarinho.quotient.common.event.MeterReading;
import io.github.marciomarinho.quotient.common.money.Currency;
import io.github.marciomarinho.quotient.common.money.Money;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import io.github.marciomarinho.quotient.rating.config.RatingProperties;
import io.github.marciomarinho.quotient.rating.config.RatingProperties.MeterPricingConfig;
import io.github.marciomarinho.quotient.rating.config.RatingProperties.PlanConfig;
import io.github.marciomarinho.quotient.rating.config.RatingProperties.PricingType;
import io.github.marciomarinho.quotient.rating.config.RatingProperties.TierConfig;
import io.github.marciomarinho.quotient.rating.plan.PlanRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RatingServiceTest {

  private static final TenantId ACME =
      TenantId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
  private static final TenantId UNCONFIGURED =
      TenantId.of(UUID.fromString("99999999-9999-9999-9999-999999999999"));
  private static final BillingWindow WINDOW =
      new BillingWindow(
          Instant.parse("2026-07-08T00:00:00Z"), Instant.parse("2026-07-08T00:01:00Z"));

  private final RatingService service = new RatingService(new PlanRepository(properties()));

  private static RatingProperties properties() {
    MeterPricingConfig tokens =
        new MeterPricingConfig(
            PricingType.TIERED,
            List.of(new TierConfig(1_000_000L, 30, 1_000), new TierConfig(null, 20, 1_000)));
    PlanConfig standard = new PlanConfig(7, Map.of("llm.tokens.input", tokens));
    return new RatingProperties(
        "in", "out", Map.of(ACME.asString(), "standard"), Map.of("standard", standard));
  }

  private static MeterReading reading(TenantId tenant, String meter, long quantity) {
    return MeterReading.of(tenant, meter, WINDOW, quantity, 3, new TreeMap<>());
  }

  @Test
  void ratesAPricedMeterAndRecordsThePlanVersion() {
    Optional<Charge> charge = service.rate(reading(ACME, "llm.tokens.input", 1_500_000));

    assertThat(charge).isPresent();
    // 1M @ 30/1k + 0.5M @ 20/1k = 40,000 minor units.
    assertThat(charge.get().amount()).isEqualTo(Money.ofMinor(40_000, Currency.AUD));
    assertThat(charge.get().planVersion()).isEqualTo(7);
    assertThat(charge.get().quantityBilled()).isEqualTo(1_500_000L);
  }

  @Test
  void reRatingProducesAnIdenticalChargeId() {
    Charge first = service.rate(reading(ACME, "llm.tokens.input", 500_000)).orElseThrow();
    Charge second = service.rate(reading(ACME, "llm.tokens.input", 500_000)).orElseThrow();

    assertThat(second.chargeId()).isEqualTo(first.chargeId());
    assertThat(second.amount()).isEqualTo(first.amount());
  }

  @Test
  void producesNoChargeForAnUnpricedMeter() {
    assertThat(service.rate(reading(ACME, "no.such.meter", 100))).isEmpty();
  }

  @Test
  void producesNoChargeForATenantWithoutAPlan() {
    assertThat(service.rate(reading(UNCONFIGURED, "llm.tokens.input", 100))).isEmpty();
  }
}
