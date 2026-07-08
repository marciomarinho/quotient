package io.github.marciomarinho.quotient.ingest.vthreads.publish;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.marciomarinho.quotient.common.event.EventJson;
import io.github.marciomarinho.quotient.common.event.UsageEvent;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes validated, enriched {@link UsageEvent}s to Kafka, keyed by tenant so a tenant's events
 * land on the same partition (ordering + downstream tenant affinity).
 *
 * <p>The send is confirmed synchronously: the calling (virtual) thread blocks on the broker ack up
 * to a bounded timeout. This is the whole point of the virtual-threads model — a straightforward
 * blocking call that scales because the carrier thread is released while parked. A failed or
 * timed-out ack raises {@link KafkaUnavailableException}, which the API turns into a 503; the
 * gateway never buffers unbounded in memory.
 */
@Component
public class UsageEventPublisher {

  private final KafkaTemplate<String, String> kafkaTemplate;
  // The gateway serializes events with the shared EventJson config so every
  // service agrees on the wire format. Built here (not injected) so it does not
  // replace Spring MVC's own ObjectMapper, which binds the request records.
  private final ObjectMapper mapper = EventJson.mapper();
  private final String topic;
  private final Duration ackTimeout;

  public UsageEventPublisher(
      KafkaTemplate<String, String> kafkaTemplate,
      @Value("${quotient.ingest.topic:usage.events.v1}") String topic,
      @Value("${quotient.ingest.ack-timeout:5s}") Duration ackTimeout) {
    this.kafkaTemplate = kafkaTemplate;
    this.topic = topic;
    this.ackTimeout = ackTimeout;
  }

  /** Publish and wait for the broker ack. Blocks; throws on failure/timeout. */
  public void publish(UsageEvent event) {
    String key = event.tenantId().asString();
    String payload = serialize(event);
    try {
      kafkaTemplate.send(topic, key, payload).get(ackTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new KafkaUnavailableException("interrupted while awaiting Kafka ack", e);
    } catch (ExecutionException | TimeoutException e) {
      throw new KafkaUnavailableException("failed to publish usage event to Kafka", e);
    }
  }

  private String serialize(UsageEvent event) {
    try {
      return mapper.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      // Serialization of our own record cannot fail in practice.
      throw new IllegalStateException("failed to serialize usage event", e);
    }
  }
}
