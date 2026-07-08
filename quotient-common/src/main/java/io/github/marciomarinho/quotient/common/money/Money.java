package io.github.marciomarinho.quotient.common.money;

import java.util.Objects;

/**
 * An exact monetary amount, stored as a whole number of <em>minor units</em> (e.g. cents) in a
 * specific {@link Currency}.
 *
 * <p>Money is the single money type for the whole platform. It never uses floating point: amounts
 * are {@code long} minor units and all arithmetic offered here is exact integer arithmetic that
 * overflows loudly ({@link Math#addExact}, {@link Math#multiplyExact}) rather than silently
 * wrapping. The only place {@link java.math.BigDecimal} is permitted is the unit-price × quantity
 * step inside the rating engine, which must round with an explicit mode and convert straight back
 * to minor units via {@link #ofMinor(long, Currency)}.
 *
 * <p>Instances are immutable value objects; arithmetic returns new instances.
 */
public record Money(long amountMinor, Currency currency) {

  public Money {
    Objects.requireNonNull(currency, "currency");
  }

  /** A money amount from a raw minor-unit count (e.g. cents). */
  public static Money ofMinor(long amountMinor, Currency currency) {
    return new Money(amountMinor, currency);
  }

  /** Zero in the given currency. */
  public static Money zero(Currency currency) {
    return new Money(0L, currency);
  }

  /**
   * A money amount from a whole number of major units (e.g. dollars). Provided for readable
   * test/seed data; production paths use {@link #ofMinor}.
   */
  public static Money ofMajor(long amountMajor, Currency currency) {
    return new Money(Math.multiplyExact(amountMajor, currency.minorUnitsPerMajor()), currency);
  }

  /** Sum of two amounts in the same currency. */
  public Money plus(Money other) {
    requireSameCurrency(other);
    return new Money(Math.addExact(amountMinor, other.amountMinor), currency);
  }

  /** Difference of two amounts in the same currency. */
  public Money minus(Money other) {
    requireSameCurrency(other);
    return new Money(Math.subtractExact(amountMinor, other.amountMinor), currency);
  }

  /** This amount scaled by a whole factor (e.g. quantity), exact. */
  public Money times(long factor) {
    return new Money(Math.multiplyExact(amountMinor, factor), currency);
  }

  /** The additive inverse. */
  public Money negate() {
    return new Money(Math.negateExact(amountMinor), currency);
  }

  public boolean isZero() {
    return amountMinor == 0L;
  }

  public boolean isPositive() {
    return amountMinor > 0L;
  }

  public boolean isNegative() {
    return amountMinor < 0L;
  }

  private void requireSameCurrency(Money other) {
    Objects.requireNonNull(other, "other");
    if (currency != other.currency) {
      throw new IllegalArgumentException(
          "Cannot combine money of different currencies: " + currency + " vs " + other.currency);
    }
  }

  /** Human-readable form, e.g. {@code AUD 12.34} or {@code AUD -0.05}. Logs/UI only, never math. */
  @Override
  public String toString() {
    long factor = currency.minorUnitsPerMajor();
    String sign = amountMinor < 0 ? "-" : "";
    long absMinor = Math.abs(amountMinor);
    long major = absMinor / factor;
    long minor = absMinor % factor;
    String pattern = "%s %s%d.%0" + currency.minorUnitScale() + "d";
    return String.format(pattern, currency, sign, major, minor);
  }
}
