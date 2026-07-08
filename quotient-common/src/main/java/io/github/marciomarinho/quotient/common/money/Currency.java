package io.github.marciomarinho.quotient.common.money;

/**
 * Supported currencies and their minor-unit scale.
 *
 * <p>The platform bills in a single currency (AUD) but the type is modelled explicitly so that
 * money arithmetic can never silently mix currencies and so that the number of minor units per
 * major unit (the scale) is a first-class, documented fact rather than a magic {@code 100}
 * scattered through the code.
 */
public enum Currency {
  /** Australian dollar. 100 cents to the dollar. */
  AUD(2);

  private final int minorUnitScale;

  Currency(int minorUnitScale) {
    this.minorUnitScale = minorUnitScale;
  }

  /**
   * Number of decimal digits in the minor unit (e.g. 2 for AUD, where the minor unit is the cent).
   * Ten raised to this power is the number of minor units in one major unit.
   */
  public int minorUnitScale() {
    return minorUnitScale;
  }

  /** Number of minor units in one major unit (e.g. 100 for AUD). */
  public long minorUnitsPerMajor() {
    long factor = 1;
    for (int i = 0; i < minorUnitScale; i++) {
      factor *= 10;
    }
    return factor;
  }
}
