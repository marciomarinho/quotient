package io.github.marciomarinho.quotient.rating.pricing;

import io.github.marciomarinho.quotient.common.money.Currency;
import io.github.marciomarinho.quotient.common.money.Money;
import java.util.Objects;

/** Flat pricing: a single rate applies to every unit, regardless of volume. */
public record FlatPrice(PriceTier rate) implements PricingModel {

  public FlatPrice {
    Objects.requireNonNull(rate, "rate");
  }

  @Override
  public Money price(long quantity, Currency currency) {
    return rate.cost(quantity, currency);
  }
}
