package io.github.marciomarinho.quotient.common.tenant;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import java.util.UUID;

/**
 * The identity of a tenant, wrapping a {@link UUID}.
 *
 * <p>A distinct type (rather than a bare {@code UUID}) so that a tenant id can never be confused
 * with any other UUID in a method signature, and so the "tenant context is mandatory" invariant is
 * visible in the type system: code that needs a tenant asks for a {@code TenantId}.
 *
 * <p>On the wire it serializes as its plain UUID string (via {@link JsonValue}) so that Kafka
 * payloads, the record key, and the JWT claim all agree on one flat representation.
 */
public record TenantId(UUID value) {

  public TenantId {
    Objects.requireNonNull(value, "tenant id value");
  }

  /** Parse from the canonical string form; throws if not a valid UUID. */
  @JsonCreator
  public static TenantId fromString(String value) {
    Objects.requireNonNull(value, "tenant id string");
    return new TenantId(UUID.fromString(value));
  }

  public static TenantId of(UUID value) {
    return new TenantId(value);
  }

  /** The canonical string form (matches the Kafka record key and the JWT claim). */
  @JsonValue
  public String asString() {
    return value.toString();
  }

  @Override
  public String toString() {
    return asString();
  }
}
