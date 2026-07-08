package io.github.marciomarinho.quotient.ingest.vthreads.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Gateway wiring: config properties, a {@link Clock}, and the OpenAPI document. */
@Configuration
@EnableConfigurationProperties(IngestProperties.class)
public class IngestConfiguration {

  /** Injectable clock so validation ("occurredAt not in the future") is testable. */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  OpenAPI ingestOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Quotient — Usage Ingestion API (virtual threads)")
                .version("v1")
                .description(
                    "High-throughput token-usage ingestion. Authenticate with a per-tenant "
                        + "API key: Authorization: Bearer <key>."))
        .schemaRequirement(
            "apiKey",
            new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .description("Per-tenant API key"));
  }
}
