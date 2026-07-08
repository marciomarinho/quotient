package io.github.marciomarinho.quotient.rating;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.marciomarinho.quotient.common.event.BillingWindow;
import io.github.marciomarinho.quotient.common.event.Charge;
import io.github.marciomarinho.quotient.common.event.EventJson;
import io.github.marciomarinho.quotient.common.event.MeterReading;
import io.github.marciomarinho.quotient.common.money.Currency;
import io.github.marciomarinho.quotient.common.money.Money;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;
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
 * End-to-end: a meter reading on {@code usage.aggregates.v1} is rated by the engine (using the real
 * configured plans) and the resulting charge appears on {@code billing.charges.v1}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RatingEngineIntegrationTest {

  private static final String IN = "usage.aggregates.v1";
  private static final String OUT = "billing.charges.v1";
  private static final TenantId ACME =
      TenantId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));

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
    registry.add("spring.kafka.consumer.group-id", () -> "it-rating-" + UUID.randomUUID());
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
  void ratesAReadingAndPublishesTheCharge() throws Exception {
    MeterReading reading =
        MeterReading.of(
            ACME,
            "llm.tokens.input",
            new BillingWindow(
                Instant.parse("2026-07-08T00:00:00Z"), Instant.parse("2026-07-08T00:01:00Z")),
            1_500_000,
            42,
            new TreeMap<>());

    try (KafkaProducer<String, String> producer = producer()) {
      producer
          .send(new ProducerRecord<>(IN, ACME.asString(), mapper.writeValueAsString(reading)))
          .get();
    }

    Charge charge = awaitCharge(Duration.ofSeconds(30));

    assertThat(charge.tenantId()).isEqualTo(ACME);
    assertThat(charge.meterCode()).isEqualTo("llm.tokens.input");
    // standard plan tiered: 1M @ 30/1k + 0.5M @ 20/1k = 40,000.
    assertThat(charge.amount()).isEqualTo(Money.ofMinor(40_000, Currency.AUD));
    assertThat(charge.planVersion()).isEqualTo(1);
  }

  private Charge awaitCharge(Duration timeout) throws Exception {
    long deadline = System.nanoTime() + timeout.toNanos();
    try (KafkaConsumer<String, String> consumer = consumer()) {
      consumer.subscribe(List.of(OUT));
      while (System.nanoTime() < deadline) {
        for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
          return mapper.readValue(record.value(), Charge.class);
        }
      }
    }
    throw new AssertionError("no charge produced within " + timeout);
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
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-charge-verify-" + UUID.randomUUID());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    return new KafkaConsumer<>(props);
  }
}
