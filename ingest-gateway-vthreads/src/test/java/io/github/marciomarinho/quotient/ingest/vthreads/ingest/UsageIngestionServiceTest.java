package io.github.marciomarinho.quotient.ingest.vthreads.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.marciomarinho.quotient.common.ingest.UsageEventRequest;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import io.github.marciomarinho.quotient.ingest.vthreads.config.IngestProperties;
import io.github.marciomarinho.quotient.ingest.vthreads.config.IngestProperties.RateLimit;
import io.github.marciomarinho.quotient.ingest.vthreads.idempotency.IdempotencyStore;
import io.github.marciomarinho.quotient.ingest.vthreads.publish.KafkaUnavailableException;
import io.github.marciomarinho.quotient.ingest.vthreads.publish.UsageEventPublisher;
import io.github.marciomarinho.quotient.ingest.vthreads.ratelimit.RateLimitExceededException;
import io.github.marciomarinho.quotient.ingest.vthreads.ratelimit.TenantRateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class UsageIngestionServiceTest {

  private static final TenantId TENANT =
      TenantId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
  private static final Instant NOW = Instant.parse("2026-07-08T00:00:00Z");

  private TenantRateLimiter rateLimiter;
  private IdempotencyStore idempotency;
  private UsageEventPublisher publisher;
  private UsageIngestionService service;

  @BeforeEach
  void setUp() {
    rateLimiter = Mockito.mock(TenantRateLimiter.class);
    idempotency = Mockito.mock(IdempotencyStore.class);
    publisher = Mockito.mock(UsageEventPublisher.class);
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    IngestProperties props =
        new IngestProperties(
            null,
            Set.of("llm.tokens.input"),
            Duration.ofMinutes(5),
            Duration.ofHours(24),
            new RateLimit(2000, 2000, Duration.ofSeconds(1)));
    service =
        new UsageIngestionService(
            rateLimiter,
            idempotency,
            publisher,
            clock,
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
            props);

    when(rateLimiter.tryConsume(any(), anyLong())).thenReturn(true);
    when(idempotency.markIfFirstSeen(any(), any())).thenReturn(true);
  }

  private static UsageEventRequest request(String key, String meter, Instant occurredAt) {
    return new UsageEventRequest(key, meter, 100, Map.of("model", "gpt-4o"), occurredAt);
  }

  @Test
  void acceptsAndPublishesAValidEvent() {
    var result = service.ingest(TENANT, request("k1", "llm.tokens.input", NOW));

    assertThat(result.deduplicated()).isFalse();
    verify(publisher).publish(any());
  }

  @Test
  void deduplicatesAndDoesNotPublishWhenKeyAlreadySeen() {
    when(idempotency.markIfFirstSeen(eq(TENANT), eq("dup"))).thenReturn(false);

    var result = service.ingest(TENANT, request("dup", "llm.tokens.input", NOW));

    assertThat(result.deduplicated()).isTrue();
    verify(publisher, never()).publish(any());
  }

  @Test
  void rejectsUnknownMeterWithoutPublishing() {
    assertThatThrownBy(() -> service.ingest(TENANT, request("k2", "no.such.meter", NOW)))
        .isInstanceOf(InvalidUsageEventException.class)
        .hasMessageContaining("unknown meter");
    verify(publisher, never()).publish(any());
  }

  @Test
  void rejectsTimestampTooFarInTheFuture() {
    Instant future = NOW.plus(Duration.ofMinutes(6));

    assertThatThrownBy(() -> service.ingest(TENANT, request("k3", "llm.tokens.input", future)))
        .isInstanceOf(InvalidUsageEventException.class)
        .hasMessageContaining("future");
  }

  @Test
  void rejectsWhenRateLimited() {
    when(rateLimiter.tryConsume(eq(TENANT), anyLong())).thenReturn(false);

    assertThatThrownBy(() -> service.ingest(TENANT, request("k4", "llm.tokens.input", NOW)))
        .isInstanceOf(RateLimitExceededException.class);
    verify(publisher, never()).publish(any());
  }

  @Test
  void releasesIdempotencyKeyWhenPublishFails() {
    Mockito.doThrow(new KafkaUnavailableException("down", null)).when(publisher).publish(any());

    assertThatThrownBy(() -> service.ingest(TENANT, request("k5", "llm.tokens.input", NOW)))
        .isInstanceOf(KafkaUnavailableException.class);
    // Key must be released so a client retry is not wrongly deduplicated.
    verify(idempotency).release(TENANT, "k5");
  }
}
