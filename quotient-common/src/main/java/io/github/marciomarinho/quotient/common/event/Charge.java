package io.github.marciomarinho.quotient.common.event;

import io.github.marciomarinho.quotient.common.money.Money;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

/**
 * A monetary charge produced by the rating engine for one meter reading, as it travels on {@code
 * billing.charges.v1}.
 *
 * <p>Wire contract between the rating engine (producer) and the ledger service (consumer). The
 * {@link #chargeId()} is <em>deterministic</em>: it is a name-based UUID of (tenant, meter, window,
 * dimensions, plan version), so re-rating the same reading with the same plan version yields the
 * same id. The ledger uses it as the transaction id and posts with {@code ON CONFLICT DO NOTHING},
 * which is what makes replaying Kafka safe — a charge can never be double-posted.
 */
public record Charge(
    int schemaVersion,
    UUID chargeId,
    TenantId tenantId,
    String meterCode,
    BillingWindow window,
    SortedMap<String, String> dimensions,
    long quantityBilled,
    Money amount,
    int planVersion) {

  public static final int CURRENT_SCHEMA_VERSION = 1;

  public Charge {
    Objects.requireNonNull(chargeId, "chargeId");
    Objects.requireNonNull(tenantId, "tenantId");
    Objects.requireNonNull(meterCode, "meterCode");
    Objects.requireNonNull(window, "window");
    Objects.requireNonNull(amount, "amount");
    if (quantityBilled < 0) {
      throw new IllegalArgumentException("quantityBilled must be >= 0, was " + quantityBilled);
    }
    dimensions =
        dimensions == null
            ? java.util.Collections.emptySortedMap()
            : java.util.Collections.unmodifiableSortedMap(new TreeMap<>(dimensions));
  }

  /**
   * Build a charge, deriving the deterministic {@link #chargeId()} from its billing coordinates.
   * Two charges with identical coordinates and plan version share an id by construction.
   */
  public static Charge of(
      TenantId tenantId,
      String meterCode,
      BillingWindow window,
      SortedMap<String, String> dimensions,
      long quantityBilled,
      Money amount,
      int planVersion) {
    SortedMap<String, String> canonical =
        dimensions == null ? new TreeMap<>() : new TreeMap<>(dimensions);
    UUID id = deterministicId(tenantId, meterCode, window, canonical, planVersion);
    return new Charge(
        CURRENT_SCHEMA_VERSION,
        id,
        tenantId,
        meterCode,
        window,
        canonical,
        quantityBilled,
        amount,
        planVersion);
  }

  private static UUID deterministicId(
      TenantId tenantId,
      String meterCode,
      BillingWindow window,
      SortedMap<String, String> dimensions,
      int planVersion) {
    StringBuilder dims = new StringBuilder();
    dimensions.forEach((k, v) -> dims.append(k).append('=').append(v).append(';'));
    String canonical =
        String.join(
            "|",
            tenantId.asString(),
            meterCode,
            window.start().toString(),
            window.end().toString(),
            dims.toString(),
            Integer.toString(planVersion));
    return UUID.nameUUIDFromBytes(canonical.getBytes(StandardCharsets.UTF_8));
  }
}
