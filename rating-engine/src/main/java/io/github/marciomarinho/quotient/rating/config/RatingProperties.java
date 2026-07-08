package io.github.marciomarinho.quotient.rating.config;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Rating configuration, bound from {@code quotient.rating.*}: which plan each tenant is on, and the
 * priced meters of each plan.
 *
 * @param inputTopic aggregated readings in
 * @param outputTopic charges out
 * @param tenantPlans tenant id (UUID string) -> plan code
 * @param plans plan code -> plan definition
 */
@ConfigurationProperties(prefix = "quotient.rating")
public record RatingProperties(
    @DefaultValue("usage.aggregates.v1") String inputTopic,
    @DefaultValue("billing.charges.v1") String outputTopic,
    Map<String, String> tenantPlans,
    Map<String, PlanConfig> plans) {

  /** Which pricing scheme a meter uses. */
  public enum PricingType {
    FLAT,
    TIERED,
    VOLUME
  }

  /** A plan definition: a version and the per-meter pricing. */
  public record PlanConfig(
      @DefaultValue("1") int version, Map<String, MeterPricingConfig> meters) {}

  /** Pricing for one meter within a plan. */
  public record MeterPricingConfig(PricingType type, List<TierConfig> tiers) {}

  /**
   * A configured tier. {@code perUnits} defaults to 1 (per-unit pricing); set it to e.g. 1000 for
   * per-1k pricing.
   */
  public record TierConfig(Long upTo, long unitPriceMinor, @DefaultValue("1") long perUnits) {}
}
