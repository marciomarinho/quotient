package io.github.marciomarinho.quotient.rating.pricing;

import io.github.marciomarinho.quotient.common.money.Currency;
import io.github.marciomarinho.quotient.common.money.Money;

/**
 * How a quantity of usage is priced. A sealed hierarchy with one implementation per pricing scheme
 * — the project's worked example of Open/Closed: adding a new scheme means adding a permitted
 * implementation, touching no existing rating code (the compiler even enforces exhaustive
 * handling).
 *
 * <p>Implementations are pure and deterministic: {@code price(quantity)} depends only on the
 * quantity and the tier definitions, so re-rating the same reading with the same plan version
 * always yields the same amount.
 */
public sealed interface PricingModel permits FlatPrice, TieredPrice, VolumePrice {

  /** The charge for {@code quantity} units in {@code currency}. */
  Money price(long quantity, Currency currency);
}
