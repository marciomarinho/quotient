package io.github.marciomarinho.quotient.rating.plan;

import io.github.marciomarinho.quotient.common.money.Currency;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import io.github.marciomarinho.quotient.rating.config.RatingProperties;
import io.github.marciomarinho.quotient.rating.config.RatingProperties.MeterPricingConfig;
import io.github.marciomarinho.quotient.rating.config.RatingProperties.PlanConfig;
import io.github.marciomarinho.quotient.rating.config.RatingProperties.TierConfig;
import io.github.marciomarinho.quotient.rating.pricing.FlatPrice;
import io.github.marciomarinho.quotient.rating.pricing.PriceTier;
import io.github.marciomarinho.quotient.rating.pricing.PricingModel;
import io.github.marciomarinho.quotient.rating.pricing.TieredPrice;
import io.github.marciomarinho.quotient.rating.pricing.VolumePrice;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Loads the versioned {@link PricePlan} for a tenant.
 *
 * <p>Plans are compiled once from configuration at startup into immutable {@link PricingModel}
 * objects and cached, so rating a reading is a map lookup — no per-charge parsing. Only
 * reference/plan data lives here; computing charges is the rating service's job (single
 * responsibility).
 */
@Repository
public class PlanRepository {

  private final Map<String, PricePlan> plansByCode = new HashMap<>();
  private final Map<String, String> tenantToPlanCode;

  public PlanRepository(RatingProperties properties) {
    this.tenantToPlanCode =
        properties.tenantPlans() == null ? Map.of() : Map.copyOf(properties.tenantPlans());
    Map<String, PlanConfig> configured = properties.plans() == null ? Map.of() : properties.plans();
    configured.forEach((code, config) -> plansByCode.put(code, toPlan(code, config)));
  }

  /** The plan for {@code tenantId}, if one is configured. */
  public Optional<PricePlan> planFor(TenantId tenantId) {
    String code = tenantToPlanCode.get(tenantId.asString());
    return Optional.ofNullable(code).map(plansByCode::get);
  }

  private static PricePlan toPlan(String code, PlanConfig config) {
    Map<String, PricingModel> pricing = new HashMap<>();
    config.meters().forEach((meter, meterConfig) -> pricing.put(meter, toModel(meterConfig)));
    return new PricePlan(code, config.version(), Currency.AUD, pricing);
  }

  private static PricingModel toModel(MeterPricingConfig config) {
    List<PriceTier> tiers = config.tiers().stream().map(PlanRepository::toTier).toList();
    return switch (config.type()) {
      case FLAT -> new FlatPrice(tiers.get(0));
      case TIERED -> new TieredPrice(tiers);
      case VOLUME -> new VolumePrice(tiers);
    };
  }

  private static PriceTier toTier(TierConfig tier) {
    return new PriceTier(tier.upTo(), tier.unitPriceMinor(), tier.perUnits());
  }
}
