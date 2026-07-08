package io.github.marciomarinho.quotient.ingest.reactive.config;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the ingestion gateway, bound from {@code quotient.ingest.*}.
 *
 * <p>Holds the per-tenant API keys (demo keys, hashed at startup), the meter catalog used for the
 * "meter exists" validation, the accepted clock skew for {@code occurredAt}, the idempotency dedup
 * window, and the per-tenant token- bucket rate limit.
 *
 * @param apiKeys per-tenant demo API keys (rotatable: up to two per tenant)
 * @param meters the set of known meter codes an event may target
 * @param maxFutureSkew how far into the future {@code occurredAt} may be
 * @param dedupTtl how long an idempotency key is remembered
 * @param rateLimit per-tenant token-bucket settings
 */
@ConfigurationProperties(prefix = "quotient.ingest")
public record IngestProperties(
    List<TenantKeys> apiKeys,
    Set<String> meters,
    @DefaultValue("5m") Duration maxFutureSkew,
    @DefaultValue("24h") Duration dedupTtl,
    RateLimit rateLimit) {

  /** API keys for one tenant. {@code tenantId} is the canonical UUID string. */
  public record TenantKeys(String tenantId, List<String> keys) {}

  /**
   * Token-bucket parameters: {@code capacity} tokens, refilling {@code refillTokens} every {@code
   * refillPeriod}. One token is consumed per event.
   */
  public record RateLimit(
      @DefaultValue("2000") long capacity,
      @DefaultValue("2000") long refillTokens,
      @DefaultValue("1s") Duration refillPeriod) {}
}
