package io.github.marciomarinho.quotient.ingest.reactive.idempotency;

import io.github.marciomarinho.quotient.common.tenant.TenantId;
import io.github.marciomarinho.quotient.ingest.reactive.config.IngestProperties;
import java.time.Duration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Reactive idempotency dedup, backed by a non-blocking Redis {@code SET NX EX}. Same semantics as
 * the vthreads gateway (dedup before publish; release on publish failure) but end-to-end reactive
 * so nothing blocks the event loop.
 */
@Component
public class ReactiveIdempotencyStore {

  private final ReactiveStringRedisTemplate redis;
  private final Duration ttl;

  public ReactiveIdempotencyStore(ReactiveStringRedisTemplate redis, IngestProperties properties) {
    this.redis = redis;
    this.ttl = properties.dedupTtl();
  }

  /** {@code true} if this is the first sighting (publish), {@code false} if a duplicate. */
  public Mono<Boolean> markIfFirstSeen(TenantId tenantId, String idempotencyKey) {
    return redis.opsForValue().setIfAbsent(redisKey(tenantId, idempotencyKey), "1", ttl);
  }

  /** Undo a mark when the subsequent publish failed. */
  public Mono<Boolean> release(TenantId tenantId, String idempotencyKey) {
    return redis.opsForValue().delete(redisKey(tenantId, idempotencyKey));
  }

  private static String redisKey(TenantId tenantId, String idempotencyKey) {
    return "idem:" + tenantId.asString() + ':' + idempotencyKey;
  }
}
