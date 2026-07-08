package io.github.marciomarinho.quotient.rating.application;

import io.github.marciomarinho.quotient.common.event.Charge;
import io.github.marciomarinho.quotient.common.event.MeterReading;
import io.github.marciomarinho.quotient.common.money.Money;
import io.github.marciomarinho.quotient.rating.plan.PlanRepository;
import io.github.marciomarinho.quotient.rating.plan.PricePlan;
import io.github.marciomarinho.quotient.rating.pricing.PricingModel;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Turns a {@link MeterReading} into a {@link Charge} by applying the tenant's versioned plan.
 *
 * <p>Deterministic by construction: the amount comes from a pure {@link PricingModel}, and the
 * charge records the {@code planVersion} used, so re-rating the same reading with the same plan
 * version yields an identical charge (same deterministic charge id and amount). A reading whose
 * tenant has no plan, or whose meter the plan does not price, produces no charge.
 */
@Service
public class RatingService {

  private final PlanRepository plans;

  public RatingService(PlanRepository plans) {
    this.plans = plans;
  }

  /** Rate a reading, or empty if the tenant/meter is not priced. */
  public Optional<Charge> rate(MeterReading reading) {
    return plans
        .planFor(reading.tenantId())
        .flatMap(
            plan ->
                plan.pricingFor(reading.meterCode()).map(model -> charge(reading, plan, model)));
  }

  private static Charge charge(MeterReading reading, PricePlan plan, PricingModel model) {
    Money amount = model.price(reading.quantity(), plan.currency());
    return Charge.of(
        reading.tenantId(),
        reading.meterCode(),
        reading.window(),
        reading.dimensions(),
        reading.quantity(),
        amount,
        plan.version());
  }
}
