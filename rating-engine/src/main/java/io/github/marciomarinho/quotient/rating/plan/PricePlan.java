package io.github.marciomarinho.quotient.rating.plan;

import io.github.marciomarinho.quotient.common.money.Currency;
import io.github.marciomarinho.quotient.rating.pricing.PricingModel;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A versioned pricing plan: the {@link PricingModel} to apply per meter code, in a single currency.
 *
 * <p>The {@code version} is recorded on every {@code Charge} the plan produces, so a charge is
 * always traceable to the exact prices used and re-rating with the same version is reproducible.
 */
public record PricePlan(
    String planCode, int version, Currency currency, Map<String, PricingModel> meterPricing) {

  public PricePlan {
    Objects.requireNonNull(planCode, "planCode");
    Objects.requireNonNull(currency, "currency");
    meterPricing = Map.copyOf(meterPricing);
  }

  /** The pricing for {@code meterCode}, if this plan prices it. */
  public Optional<PricingModel> pricingFor(String meterCode) {
    return Optional.ofNullable(meterPricing.get(meterCode));
  }
}
