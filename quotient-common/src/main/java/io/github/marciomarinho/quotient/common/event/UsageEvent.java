package io.github.marciomarinho.quotient.common.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A single token-usage telemetry event as it travels on {@code usage.events.v1}.
 *
 * <p>This is the wire contract between the ingestion gateways (producers) and the meter aggregator
 * (consumer). It is immutable and self-describing: the {@code schemaVersion} lets consumers evolve
 * without guessing, and matches the topic suffix ({@code .v1}).
 *
 * <p>{@code dimensions} (e.g. {@code model=gpt-4o}, {@code region=ap-southeast-2}) are held in a
 * sorted, unmodifiable map so that a given set of dimensions has a single canonical form — the
 * aggregator groups on {@link #dimensionsKey()}, which must be stable regardless of insertion
 * order.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UsageEvent(
    int schemaVersion,
    String idempotencyKey,
    TenantId tenantId,
    String meterCode,
    long quantity,
    SortedMap<String, String> dimensions,
    Instant occurredAt,
    Instant receivedAt) {

  /** Current schema version emitted by producers. */
  public static final int CURRENT_SCHEMA_VERSION = 1;

  public UsageEvent {
    Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    Objects.requireNonNull(tenantId, "tenantId");
    Objects.requireNonNull(meterCode, "meterCode");
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(receivedAt, "receivedAt");
    if (quantity < 0) {
      throw new IllegalArgumentException("quantity must be >= 0, was " + quantity);
    }
    // Canonicalise dimensions: sorted, unmodifiable, never null.
    dimensions =
        dimensions == null
            ? java.util.Collections.emptySortedMap()
            : java.util.Collections.unmodifiableSortedMap(new TreeMap<>(dimensions));
  }

  /**
   * Factory stamping the current schema version and receive time. Used by the gateway when it
   * accepts an event.
   */
  public static UsageEvent received(
      String idempotencyKey,
      TenantId tenantId,
      String meterCode,
      long quantity,
      Map<String, String> dimensions,
      Instant occurredAt,
      Instant receivedAt) {
    return new UsageEvent(
        CURRENT_SCHEMA_VERSION,
        idempotencyKey,
        tenantId,
        meterCode,
        quantity,
        dimensions == null ? null : new TreeMap<>(dimensions),
        occurredAt,
        receivedAt);
  }

  /**
   * A stable string key for the dimension set, used to group events into readings. Empty dimensions
   * yield {@code ""}. Format: {@code k1=v1;k2=v2} in sorted key order.
   */
  public String dimensionsKey() {
    if (dimensions.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> e : dimensions.entrySet()) {
      if (sb.length() > 0) {
        sb.append(';');
      }
      sb.append(e.getKey()).append('=').append(e.getValue());
    }
    return sb.toString();
  }
}
