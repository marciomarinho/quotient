package io.github.marciomarinho.quotient.aggregator.topology;

import io.github.marciomarinho.quotient.common.event.UsageEvent;
import io.github.marciomarinho.quotient.common.meter.Aggregation;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The running accumulator for one (tenant, meter, dimensions) within one window.
 *
 * <p>It carries the identity fields (set from the first event folded in) so the final {@code
 * MeterReading} can be emitted without re-parsing the grouping key, plus the aggregated {@code
 * value} and the {@code eventCount}. The aggregated value depends on the meter's {@link
 * Aggregation}: SUM accumulates quantities, MAX keeps the largest, and COUNT tracks the number of
 * events (equal to {@code eventCount}).
 */
public record MeterAggregate(
    String tenantId,
    String meterCode,
    SortedMap<String, String> dimensions,
    Aggregation aggregation,
    long value,
    long eventCount) {

  /** The identity element for a fresh window. */
  public static MeterAggregate empty() {
    return new MeterAggregate(null, null, null, null, 0L, 0L);
  }

  /** Fold {@code event} in under the meter's {@code aggregation}. */
  public MeterAggregate add(UsageEvent event, Aggregation agg) {
    long newCount = eventCount + 1;
    long newValue =
        switch (agg) {
          case SUM -> value + event.quantity();
          case MAX -> Math.max(value, event.quantity());
          case COUNT -> newCount;
        };
    return new MeterAggregate(
        event.tenantId().asString(),
        event.meterCode(),
        new TreeMap<>(event.dimensions()),
        agg,
        newValue,
        newCount);
  }
}
