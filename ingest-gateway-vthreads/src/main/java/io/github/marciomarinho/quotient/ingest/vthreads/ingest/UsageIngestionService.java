package io.github.marciomarinho.quotient.ingest.vthreads.ingest;

import io.github.marciomarinho.quotient.common.event.UsageEvent;
import io.github.marciomarinho.quotient.common.ingest.BatchIngestResponse;
import io.github.marciomarinho.quotient.common.ingest.IngestAcceptance;
import io.github.marciomarinho.quotient.common.ingest.UsageEventRequest;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import io.github.marciomarinho.quotient.ingest.vthreads.config.IngestProperties;
import io.github.marciomarinho.quotient.ingest.vthreads.idempotency.IdempotencyStore;
import io.github.marciomarinho.quotient.ingest.vthreads.publish.UsageEventPublisher;
import io.github.marciomarinho.quotient.ingest.vthreads.ratelimit.RateLimitExceededException;
import io.github.marciomarinho.quotient.ingest.vthreads.ratelimit.TenantRateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Applies the ingestion contract to each event: per-tenant rate limiting, then validation (meter
 * exists, {@code occurredAt} not too far in the future), then idempotent dedup, then publish to
 * Kafka.
 *
 * <p>Dedup happens before publish; if the publish fails the dedup mark is released so a client
 * retry is not silently dropped. Rate limiting is charged one token per event (a 1,000-event batch
 * consumes 1,000 tokens).
 */
@Service
public class UsageIngestionService {

  private final TenantRateLimiter rateLimiter;
  private final IdempotencyStore idempotency;
  private final UsageEventPublisher publisher;
  private final Clock clock;
  private final Set<String> knownMeters;
  private final Duration maxFutureSkew;

  public UsageIngestionService(
      TenantRateLimiter rateLimiter,
      IdempotencyStore idempotency,
      UsageEventPublisher publisher,
      Clock clock,
      IngestProperties properties) {
    this.rateLimiter = rateLimiter;
    this.idempotency = idempotency;
    this.publisher = publisher;
    this.clock = clock;
    this.knownMeters = properties.meters() == null ? Set.of() : properties.meters();
    this.maxFutureSkew = properties.maxFutureSkew();
  }

  /** Ingest a single event for {@code tenant}. */
  public IngestAcceptance ingest(TenantId tenant, UsageEventRequest request) {
    charge(tenant, 1);
    return ingestOne(tenant, request);
  }

  /** Ingest a batch for {@code tenant}, one token charged per event. */
  public BatchIngestResponse ingestBatch(TenantId tenant, List<UsageEventRequest> requests) {
    charge(tenant, requests.size());
    List<IngestAcceptance> results = requests.stream().map(r -> ingestOne(tenant, r)).toList();
    return BatchIngestResponse.of(results);
  }

  private IngestAcceptance ingestOne(TenantId tenant, UsageEventRequest request) {
    validate(request);
    if (!idempotency.markIfFirstSeen(tenant, request.idempotencyKey())) {
      return IngestAcceptance.deduplicated(request.idempotencyKey());
    }
    UsageEvent event =
        UsageEvent.received(
            request.idempotencyKey(),
            tenant,
            request.meterCode(),
            request.quantity(),
            request.dimensions(),
            request.occurredAt(),
            Instant.now(clock));
    try {
      publisher.publish(event);
    } catch (RuntimeException e) {
      // Publish failed: undo the dedup mark so the client's retry is accepted.
      idempotency.release(tenant, request.idempotencyKey());
      throw e;
    }
    return IngestAcceptance.accepted(request.idempotencyKey());
  }

  private void charge(TenantId tenant, long tokens) {
    if (!rateLimiter.tryConsume(tenant, tokens)) {
      throw new RateLimitExceededException(tenant);
    }
  }

  private void validate(UsageEventRequest request) {
    if (!knownMeters.contains(request.meterCode())) {
      throw new InvalidUsageEventException("unknown meter code: " + request.meterCode());
    }
    Instant latestAllowed = Instant.now(clock).plus(maxFutureSkew);
    if (request.occurredAt().isAfter(latestAllowed)) {
      throw new InvalidUsageEventException(
          "occurredAt "
              + request.occurredAt()
              + " is more than "
              + maxFutureSkew
              + " in the future");
    }
  }
}
