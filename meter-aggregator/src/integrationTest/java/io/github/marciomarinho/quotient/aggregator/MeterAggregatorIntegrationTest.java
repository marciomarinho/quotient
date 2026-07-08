package io.github.marciomarinho.quotient.aggregator;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.marciomarinho.quotient.common.event.EventJson;
import io.github.marciomarinho.quotient.common.event.MeterReading;
import io.github.marciomarinho.quotient.common.event.UsageEvent;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.ConfluentKafkaContainer;

/**
 * End-to-end test of the aggregator against a real Kafka broker with exactly-once processing.
 * Events are produced with controlled timestamps; a later "trigger" event on the same grouping key
 * advances stream time past the window + grace so the suppressed window emits its final reading,
 * which we consume from {@code usage.aggregates.v1}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MeterAggregatorIntegrationTest {

  private static final String IN = "usage.events.v1";
  private static final String OUT = "usage.aggregates.v1";
  private static final TenantId ACME =
      TenantId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
  // Aligned to a 2s window boundary.
  private static final Instant BASE = Instant.parse("2026-07-08T00:00:00Z");

  private final ObjectMapper mapper = EventJson.mapper();

  static final ConfluentKafkaContainer KAFKA =
      new ConfluentKafkaContainer("confluentinc/cp-kafka:7.8.0");

  static {
    KAFKA.start();
    createTopics();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    registry.add("spring.kafka.streams.application-id", () -> "it-aggregator-" + UUID.randomUUID());
    // Small window/grace so the test closes a window quickly.
    registry.add("quotient.aggregator.window-size", () -> "2s");
    registry.add("quotient.aggregator.grace", () -> "1s");
  }

  private static void createTopics() {
    Properties props = new Properties();
    props.put("bootstrap.servers", KAFKA.getBootstrapServers());
    try (Admin admin = Admin.create(props)) {
      admin
          .createTopics(List.of(new NewTopic(IN, 1, (short) 1), new NewTopic(OUT, 1, (short) 1)))
          .all()
          .get();
    } catch (Exception e) {
      throw new IllegalStateException("failed to create topics", e);
    }
  }

  @Test
  void aggregatesAWindowAndEmitsAReadingToTheAggregatesTopic() throws Exception {
    try (KafkaProducer<String, String> producer = producer()) {
      // Three events inside window [BASE, BASE+2s).
      publish(producer, event(100, BASE.plusMillis(0)));
      publish(producer, event(200, BASE.plusMillis(500)));
      publish(producer, event(300, BASE.plusMillis(1000)));
      // Trigger on the SAME grouping key, past window+grace (2s+1s), to close it.
      publish(producer, event(1, BASE.plusMillis(4000)));
      producer.flush();
    }

    MeterReading reading = awaitReadingForWindow(BASE, Duration.ofSeconds(40));

    assertThat(reading.tenantId()).isEqualTo(ACME);
    assertThat(reading.meterCode()).isEqualTo("llm.tokens.input");
    assertThat(reading.quantity()).as("SUM of the three in-window events").isEqualTo(600L);
    assertThat(reading.eventCount()).isEqualTo(3L);
  }

  private UsageEvent event(long quantity, Instant at) {
    return UsageEvent.received(
        "idem-" + at.toEpochMilli(),
        ACME,
        "llm.tokens.input",
        quantity,
        Map.of("model", "gpt-4o"),
        at,
        at);
  }

  private void publish(KafkaProducer<String, String> producer, UsageEvent event) throws Exception {
    String value = mapper.writeValueAsString(event);
    producer.send(
        new ProducerRecord<>(IN, null, event.occurredAt().toEpochMilli(), ACME.asString(), value));
  }

  private MeterReading awaitReadingForWindow(Instant windowStart, Duration timeout)
      throws Exception {
    long deadline = System.nanoTime() + timeout.toNanos();
    try (KafkaConsumer<String, String> consumer = consumer()) {
      consumer.subscribe(List.of(OUT));
      List<MeterReading> seen = new ArrayList<>();
      while (System.nanoTime() < deadline) {
        for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
          MeterReading reading = mapper.readValue(record.value(), MeterReading.class);
          seen.add(reading);
          if (reading.window().start().equals(windowStart)
              && reading.meterCode().equals("llm.tokens.input")) {
            return reading;
          }
        }
      }
      throw new AssertionError("no reading for window " + windowStart + "; saw " + seen);
    }
  }

  private KafkaProducer<String, String> producer() {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    return new KafkaProducer<>(props);
  }

  private KafkaConsumer<String, String> consumer() {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-verify-" + UUID.randomUUID());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    // Read only committed records, proving exactly-once output.
    props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
    return new KafkaConsumer<>(props);
  }
}
