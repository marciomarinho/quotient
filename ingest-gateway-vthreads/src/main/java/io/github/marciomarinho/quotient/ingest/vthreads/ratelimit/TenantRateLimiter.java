package io.github.marciomarinho.quotient.ingest.vthreads.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import io.github.marciomarinho.quotient.ingest.vthreads.config.IngestProperties;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Per-tenant token-bucket rate limiting — the noisy-neighbour protection. Each tenant gets its own
 * {@link Bucket} sized from {@code quotient.ingest.rate-limit}; one token is consumed per event.
 * When a tenant's bucket is empty its requests are rejected (429) without affecting any other
 * tenant.
 */
@Component
public class TenantRateLimiter {

  private final ConcurrentHashMap<TenantId, Bucket> buckets = new ConcurrentHashMap<>();
  private final IngestProperties.RateLimit config;

  public TenantRateLimiter(IngestProperties properties) {
    this.config = properties.rateLimit();
  }

  /**
   * Try to consume {@code tokens} for {@code tenantId}. Returns {@code true} if the tokens were
   * available (request allowed), {@code false} if the tenant is over its limit (429).
   */
  public boolean tryConsume(TenantId tenantId, long tokens) {
    return bucketFor(tenantId).tryConsume(tokens);
  }

  private Bucket bucketFor(TenantId tenantId) {
    return buckets.computeIfAbsent(tenantId, id -> newBucket());
  }

  private Bucket newBucket() {
    Bandwidth limit =
        Bandwidth.builder()
            .capacity(config.capacity())
            .refillGreedy(config.refillTokens(), config.refillPeriod())
            .build();
    return Bucket.builder().addLimit(limit).build();
  }
}
