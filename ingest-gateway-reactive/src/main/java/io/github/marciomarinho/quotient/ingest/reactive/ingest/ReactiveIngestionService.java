package io.github.marciomarinho.quotient.ingest.reactive.ingest;

import io.github.marciomarinho.quotient.common.event.UsageEvent;
import io.github.marciomarinho.quotient.common.ingest.BatchIngestResponse;
import io.github.marciomarinho.quotient.common.ingest.IngestAcceptance;
import io.github.marciomarinho.quotient.common.ingest.UsageEventRequest;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import io.github.marciomarinho.quotient.ingest.reactive.config.IngestProperties;
import io.github.marciomarinho.quotient.ingest.reactive.idempotency.ReactiveIdempotencyStore;
import io.github.marciomarinho.quotient.ingest.reactive.publish.ReactiveUsageEventPublisher;
import io.github.marciomarinho.quotient.ingest.reactive.ratelimit.RateLimitExceededException;
import io.github.marciomarinho.quotient.ingest.reactive.ratelimit.TenantRateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive version of the ingestion contract: rate limit → validate → dedup → publish, entirely as
 * a non-blocking reactor chain. Behaviour matches the vthreads gateway (dedup before publish;
 * release the mark if publish fails) so both satisfy the same contract tests.
 */
@Service
public class ReactiveIngestionService {

  private final TenantRateLimiter rateLimiter;
  private final ReactiveIdempotencyStore idempotency;
  private final ReactiveUsageEventPublisher publisher;
  private final Clock clock;
  private final Set<String> knownMeters;
  private final Duration maxFutureSkew;

  public ReactiveIngestionService(
      TenantRateLimiter rateLimiter,
      ReactiveIdempotencyStore idempotency,
      ReactiveUsageEventPublisher publisher,
      Clock clock,
      IngestProperties properties) {
    this.rateLimiter = rateLimiter;
    this.idempotency = idempotency;
    this.publisher = publisher;
    this.clock = clock;
    this.knownMeters = properties.meters() == null ? Set.of() : properties.meters();
    this.maxFutureSkew = properties.maxFutureSkew();
  }

  /** Ingest a single event. */
  public Mono<IngestAcceptance> ingest(TenantId tenant, UsageEventRequest request) {
    return charge(tenant, 1).then(ingestOne(tenant, request));
  }

  /** Ingest a batch, one token charged per event, preserving order. */
  public Mono<BatchIngestResponse> ingestBatch(TenantId tenant, List<UsageEventRequest> requests) {
    return charge(tenant, requests.size())
        .thenMany(Flux.fromIterable(requests).concatMap(r -> ingestOne(tenant, r)))
        .collectList()
        .map(BatchIngestResponse::of);
  }

  private Mono<IngestAcceptance> ingestOne(TenantId tenant, UsageEventRequest request) {
    return Mono.defer(
        () -> {
          validate(request);
          return idempotency
              .markIfFirstSeen(tenant, request.idempotencyKey())
              .flatMap(
                  first -> {
                    if (Boolean.FALSE.equals(first)) {
                      return Mono.just(IngestAcceptance.deduplicated(request.idempotencyKey()));
                    }
                    return publisher
                        .publish(toEvent(tenant, request))
                        .thenReturn(IngestAcceptance.accepted(request.idempotencyKey()))
                        .onErrorResume(
                            e ->
                                idempotency
                                    .release(tenant, request.idempotencyKey())
                                    .then(Mono.error(e)));
                  });
        });
  }

  private UsageEvent toEvent(TenantId tenant, UsageEventRequest request) {
    return UsageEvent.received(
        request.idempotencyKey(),
        tenant,
        request.meterCode(),
        request.quantity(),
        request.dimensions(),
        request.occurredAt(),
        Instant.now(clock));
  }

  private Mono<Void> charge(TenantId tenant, long tokens) {
    return Mono.defer(
        () ->
            rateLimiter.tryConsume(tenant, tokens)
                ? Mono.<Void>empty()
                : Mono.error(new RateLimitExceededException(tenant)));
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
