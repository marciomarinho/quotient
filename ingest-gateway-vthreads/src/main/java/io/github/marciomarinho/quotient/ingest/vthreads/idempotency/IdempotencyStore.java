package io.github.marciomarinho.quotient.ingest.vthreads.idempotency;

import io.github.marciomarinho.quotient.common.tenant.TenantId;
import io.github.marciomarinho.quotient.ingest.vthreads.config.IngestProperties;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Deduplicates events by their client-supplied idempotency key using a Redis {@code SET key value
 * NX EX ttl}. The key is remembered for the configured dedup window; within it, a repeat is
 * reported as deduplicated and not re-published.
 *
 * <p>Keys are namespaced by tenant so two tenants' identical idempotency keys never collide. The
 * check happens <em>before</em> the Kafka publish (Kafka's exactly-once semantics cover the
 * downstream). To avoid losing an event if the publish then fails, callers must {@link #release}
 * the key so a client retry is not wrongly deduplicated — the trade-off is documented in
 * docs/EXACTLY_ONCE.md.
 */
@Component
public class IdempotencyStore {

  private final StringRedisTemplate redis;
  private final Duration ttl;

  public IdempotencyStore(StringRedisTemplate redis, IngestProperties properties) {
    this.redis = redis;
    this.ttl = properties.dedupTtl();
  }

  /**
   * Record the key if unseen. Returns {@code true} if this is the first sighting (caller should
   * publish), {@code false} if it is a duplicate within the window.
   */
  public boolean markIfFirstSeen(TenantId tenantId, String idempotencyKey) {
    Boolean set = redis.opsForValue().setIfAbsent(redisKey(tenantId, idempotencyKey), "1", ttl);
    return Boolean.TRUE.equals(set);
  }

  /** Undo a {@link #markIfFirstSeen} when the subsequent publish failed. */
  public void release(TenantId tenantId, String idempotencyKey) {
    redis.delete(redisKey(tenantId, idempotencyKey));
  }

  private static String redisKey(TenantId tenantId, String idempotencyKey) {
    return "idem:" + tenantId.asString() + ':' + idempotencyKey;
  }
}
