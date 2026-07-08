package io.github.marciomarinho.quotient.ingest.reactive.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

/** Reactive gateway wiring: config properties, clock, reactor-kafka sender, OpenAPI. */
@Configuration
@EnableConfigurationProperties(IngestProperties.class)
public class IngestConfiguration {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  /** A reactor-kafka sender with an idempotent, acks=all producer. */
  @Bean
  KafkaSender<String, String> kafkaSender(
      @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
    Map<String, Object> config = new HashMap<>();
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    config.put(ProducerConfig.ACKS_CONFIG, "all");
    config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
    config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 8000);
    config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 4000);
    return KafkaSender.create(SenderOptions.create(config));
  }

  @Bean
  OpenAPI ingestOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Quotient — Usage Ingestion API (reactive)")
                .version("v1")
                .description(
                    "High-throughput token-usage ingestion (WebFlux). Authenticate with a "
                        + "per-tenant API key: Authorization: Bearer <key>."))
        .schemaRequirement(
            "apiKey",
            new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .description("Per-tenant API key"));
  }
}
