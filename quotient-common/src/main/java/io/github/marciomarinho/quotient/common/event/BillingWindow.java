package io.github.marciomarinho.quotient.common.event;

import java.time.Instant;
import java.util.Objects;

/**
 * A half-open time window {@code [start, end)} that a meter reading (and the charge derived from
 * it) covers.
 *
 * <p>Windows are the unit of aggregation: the aggregator emits one reading per (tenant, meter,
 * dimensions) per window, and the rating engine and ledger key off the same window so re-processing
 * is idempotent.
 */
public record BillingWindow(Instant start, Instant end) {

  public BillingWindow {
    Objects.requireNonNull(start, "window start");
    Objects.requireNonNull(end, "window end");
    if (!end.isAfter(start)) {
      throw new IllegalArgumentException("window end " + end + " must be after start " + start);
    }
  }

  /** True if {@code instant} falls within {@code [start, end)}. */
  public boolean contains(Instant instant) {
    return !instant.isBefore(start) && instant.isBefore(end);
  }
}
