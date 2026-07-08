package io.github.marciomarinho.quotient.common.event;

import io.github.marciomarinho.quotient.common.tenant.TenantId;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The aggregated usage for one (tenant, meter, dimensions) over one window, as it travels on {@code
 * usage.aggregates.v1}.
 *
 * <p>Wire contract between the meter aggregator (producer) and the rating engine (consumer). {@code
 * quantity} is the aggregated value per the meter's aggregation function; {@code eventCount} is how
 * many raw events contributed (useful for COUNT meters and for audit).
 */
public record MeterReading(
    int schemaVersion,
    TenantId tenantId,
    String meterCode,
    BillingWindow window,
    long quantity,
    long eventCount,
    SortedMap<String, String> dimensions) {

  public static final int CURRENT_SCHEMA_VERSION = 1;

  public MeterReading {
    Objects.requireNonNull(tenantId, "tenantId");
    Objects.requireNonNull(meterCode, "meterCode");
    Objects.requireNonNull(window, "window");
    if (quantity < 0) {
      throw new IllegalArgumentException("reading quantity must be >= 0, was " + quantity);
    }
    if (eventCount < 0) {
      throw new IllegalArgumentException("eventCount must be >= 0, was " + eventCount);
    }
    dimensions =
        dimensions == null
            ? java.util.Collections.emptySortedMap()
            : java.util.Collections.unmodifiableSortedMap(new TreeMap<>(dimensions));
  }

  public static MeterReading of(
      TenantId tenantId,
      String meterCode,
      BillingWindow window,
      long quantity,
      long eventCount,
      SortedMap<String, String> dimensions) {
    return new MeterReading(
        CURRENT_SCHEMA_VERSION, tenantId, meterCode, window, quantity, eventCount, dimensions);
  }
}
