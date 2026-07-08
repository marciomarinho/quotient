package io.github.marciomarinho.quotient.common.tenant;

import java.util.Objects;
import java.util.Optional;

/**
 * Holds the current tenant for the duration of a unit of work on a single thread (or virtual
 * thread).
 *
 * <p>This is the in-process carrier of the tenant-context invariant: an inbound request resolves
 * its tenant (from an API key or a {@code tenant_id} JWT claim), binds it here, and downstream code
 * — Kafka key selection, Postgres {@code SET LOCAL app.tenant_id} — reads it back. It is
 * deliberately framework-free so both the reactive and MVC gateways and the ledger service share
 * the exact same notion of "who is this for".
 *
 * <p>Backed by a plain {@link ThreadLocal}. It is safe on virtual threads (each carries its own
 * binding); reactive code must instead propagate the {@link TenantId} through the Reactor context
 * and bind it only around synchronous sections. Always pair {@link #set} with {@link #clear} in a
 * {@code try/finally}, or use {@link #runWith}.
 */
public final class TenantContext {

  private static final ThreadLocal<TenantId> CURRENT = new ThreadLocal<>();

  private TenantContext() {}

  /** Bind {@code tenantId} as the current tenant on this thread. */
  public static void set(TenantId tenantId) {
    CURRENT.set(Objects.requireNonNull(tenantId, "tenantId"));
  }

  /** The current tenant, if one is bound. */
  public static Optional<TenantId> current() {
    return Optional.ofNullable(CURRENT.get());
  }

  /**
   * The current tenant or fail. Use on tenant-scoped paths where the absence of a tenant is a
   * programming error, not an expected state.
   */
  public static TenantId require() {
    TenantId id = CURRENT.get();
    if (id == null) {
      throw new IllegalStateException(
          "No tenant bound in TenantContext; tenant context is mandatory on tenant-scoped operations");
    }
    return id;
  }

  /** Remove any bound tenant. Must be called to avoid leaking across pooled threads. */
  public static void clear() {
    CURRENT.remove();
  }

  /** Run {@code action} with {@code tenantId} bound, clearing it afterwards. */
  public static void runWith(TenantId tenantId, Runnable action) {
    set(tenantId);
    try {
      action.run();
    } finally {
      clear();
    }
  }
}
