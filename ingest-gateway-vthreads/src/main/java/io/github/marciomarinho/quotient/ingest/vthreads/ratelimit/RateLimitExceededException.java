package io.github.marciomarinho.quotient.ingest.vthreads.ratelimit;

import io.github.marciomarinho.quotient.common.tenant.TenantId;

/** A tenant exceeded its ingestion rate limit. Maps to 429. */
public class RateLimitExceededException extends RuntimeException {

  public RateLimitExceededException(TenantId tenantId) {
    super("rate limit exceeded for tenant " + tenantId.asString());
  }
}
