package io.github.marciomarinho.quotient.ingest.reactive;

import com.redis.testcontainers.RedisContainer;
import io.restassured.RestAssured;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.ConfluentKafkaContainer;

/**
 * Base for gateway integration/contract tests: a shared Kafka and Redis started once for the suite,
 * with the app under test bound to them. Also offers a small helper to drain {@code
 * usage.events.v1} so tests can assert what was actually published.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractGatewayIntegrationTest {

  protected static final String TOPIC = "usage.events.v1";

  static final ConfluentKafkaContainer KAFKA =
      new ConfluentKafkaContainer("confluentinc/cp-kafka:7.8.0");

  @SuppressWarnings("resource")
  static final RedisContainer REDIS = new RedisContainer("redis:7-alpine");

  static {
    KAFKA.start();
    REDIS.start();
  }

  @LocalServerPort int port;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
  }

  @BeforeEach
  void configureRestAssured() {
    RestAssured.baseURI = "http://localhost";
    RestAssured.port = port;
  }

  /** Poll {@code usage.events.v1} for up to {@code timeout}, returning the record values seen. */
  protected List<String> drainUsageEvents(Duration timeout) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-consumer-" + System.nanoTime());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

    List<String> values = new ArrayList<>();
    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
      consumer.subscribe(List.of(TOPIC));
      long deadline = System.nanoTime() + timeout.toNanos();
      while (System.nanoTime() < deadline) {
        for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(300))) {
          values.add(record.value());
        }
        if (!values.isEmpty()) {
          break;
        }
      }
    }
    return values;
  }

  /** Demo API keys from application.yml, for readability in tests. */
  protected static Map<String, String> demoKeys() {
    return Map.of(
        "acme", "qk_live_acme_primary",
        "globex", "qk_live_globex_primary",
        "initech", "qk_live_initech_primary");
  }
}
