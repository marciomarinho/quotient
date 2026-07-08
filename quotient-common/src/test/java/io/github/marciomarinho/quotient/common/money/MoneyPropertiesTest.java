package io.github.marciomarinho.quotient.common.money;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

/**
 * Property-based invariants for {@link Money} arithmetic. Bounds are kept away from {@link
 * Long#MAX_VALUE} so exact-arithmetic overflow does not obscure the algebraic properties being
 * asserted (overflow itself is covered by an example test in {@link MoneyTest}).
 */
class MoneyPropertiesTest {

  @Property
  void plusIsCommutative(
      @ForAll @LongRange(min = -1_000_000_000L, max = 1_000_000_000L) long a,
      @ForAll @LongRange(min = -1_000_000_000L, max = 1_000_000_000L) long b) {
    Money ma = Money.ofMinor(a, Currency.AUD);
    Money mb = Money.ofMinor(b, Currency.AUD);

    assertThat(ma.plus(mb)).isEqualTo(mb.plus(ma));
  }

  @Property
  void minusIsInverseOfPlus(
      @ForAll @LongRange(min = -1_000_000_000L, max = 1_000_000_000L) long a,
      @ForAll @LongRange(min = -1_000_000_000L, max = 1_000_000_000L) long b) {
    Money ma = Money.ofMinor(a, Currency.AUD);
    Money mb = Money.ofMinor(b, Currency.AUD);

    assertThat(ma.plus(mb).minus(mb)).isEqualTo(ma);
  }

  @Property
  void timesDistributesOverPlus(
      @ForAll @LongRange(min = -1_000_000L, max = 1_000_000L) long a,
      @ForAll @LongRange(min = -1_000_000L, max = 1_000_000L) long b,
      @ForAll @IntRange(min = -1000, max = 1000) int factor) {
    Money ma = Money.ofMinor(a, Currency.AUD);
    Money mb = Money.ofMinor(b, Currency.AUD);

    assertThat(ma.plus(mb).times(factor)).isEqualTo(ma.times(factor).plus(mb.times(factor)));
  }

  @Property
  void negateIsSelfInverse(@ForAll @LongRange(min = -1_000_000_000L, max = 1_000_000_000L) long a) {
    Money ma = Money.ofMinor(a, Currency.AUD);

    assertThat(ma.negate().negate()).isEqualTo(ma);
  }
}
