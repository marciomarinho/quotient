package io.github.marciomarinho.quotient.ingest.reactive.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import io.github.marciomarinho.quotient.ingest.reactive.config.IngestProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Resolves an inbound API key to a {@link TenantId}.
 *
 * <p>Keys are never stored in plaintext: the configured demo keys are hashed once at startup with
 * Argon2id, and inbound keys are verified against those hashes. Because Argon2 is deliberately
 * expensive, successful verifications are memoized in a bounded Caffeine cache so the hot path
 * costs a hash lookup, not an Argon2 computation — matching how high-volume ingest APIs
 * authenticate.
 *
 * <p>Rotation is supported: a tenant may present either of up to two active keys (see {@link
 * IngestProperties.TenantKeys}).
 */
@Component
public class ApiKeyStore {

  private static final Logger LOG = LoggerFactory.getLogger(ApiKeyStore.class);

  private final Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
  private final List<HashedKey> hashedKeys = new ArrayList<>();
  private final Cache<String, TenantId> verifiedCache =
      Caffeine.newBuilder().maximumSize(10_000).expireAfterAccess(Duration.ofHours(1)).build();

  public ApiKeyStore(IngestProperties properties) {
    List<IngestProperties.TenantKeys> configured =
        properties.apiKeys() == null ? List.of() : properties.apiKeys();
    for (IngestProperties.TenantKeys tenantKeys : configured) {
      TenantId tenantId = TenantId.fromString(tenantKeys.tenantId());
      for (String rawKey : tenantKeys.keys()) {
        String hash = argon2.hash(2, 65_536, 1, rawKey.toCharArray());
        hashedKeys.add(new HashedKey(tenantId, hash));
      }
    }
    LOG.info("loaded {} API key(s) across {} tenant(s)", hashedKeys.size(), configured.size());
  }

  /** The tenant owning {@code rawKey}, or empty if it matches no active key. */
  public Optional<TenantId> resolve(String rawKey) {
    if (rawKey == null || rawKey.isBlank()) {
      return Optional.empty();
    }
    TenantId cached = verifiedCache.getIfPresent(rawKey);
    if (cached != null) {
      return Optional.of(cached);
    }
    char[] chars = rawKey.toCharArray();
    for (HashedKey candidate : hashedKeys) {
      if (argon2.verify(candidate.hash(), chars)) {
        verifiedCache.put(rawKey, candidate.tenantId());
        return Optional.of(candidate.tenantId());
      }
    }
    return Optional.empty();
  }

  private record HashedKey(TenantId tenantId, String hash) {}
}
