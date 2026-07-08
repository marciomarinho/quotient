package io.github.marciomarinho.quotient.ingest.reactive.publish;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.marciomarinho.quotient.common.event.EventJson;
import io.github.marciomarinho.quotient.common.event.UsageEvent;
import java.time.Duration;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

/**
 * Publishes usage events to Kafka reactively via reactor-kafka — the send never blocks the event
 * loop. The returned {@link Mono} completes when the broker acks and errors ({@link
 * KafkaUnavailableException}) on failure or timeout, so the API can answer 503 without ever
 * buffering unbounded.
 */
@Component
public class ReactiveUsageEventPublisher {

  private final KafkaSender<String, String> sender;
  private final ObjectMapper mapper = EventJson.mapper();
  private final String topic;
  private final Duration ackTimeout;

  public ReactiveUsageEventPublisher(
      KafkaSender<String, String> sender,
      @Value("${quotient.ingest.topic:usage.events.v1}") String topic,
      @Value("${quotient.ingest.ack-timeout:5s}") Duration ackTimeout) {
    this.sender = sender;
    this.topic = topic;
    this.ackTimeout = ackTimeout;
  }

  /** Publish and complete on broker ack; errors on failure/timeout. */
  public Mono<Void> publish(UsageEvent event) {
    String key = event.tenantId().asString();
    return Mono.fromCallable(() -> mapper.writeValueAsString(event))
        .flatMap(
            payload -> {
              SenderRecord<String, String, String> record =
                  SenderRecord.create(new ProducerRecord<>(topic, key, payload), key);
              return sender.send(Mono.just(record)).next();
            })
        .timeout(ackTimeout)
        .flatMap(
            result ->
                result.exception() == null
                    ? Mono.empty()
                    : Mono.error(
                        new KafkaUnavailableException(
                            "failed to publish usage event to Kafka", result.exception())))
        .onErrorMap(
            e -> !(e instanceof KafkaUnavailableException),
            e -> new KafkaUnavailableException("failed to publish usage event to Kafka", e))
        .then();
  }
}
