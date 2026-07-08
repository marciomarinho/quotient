package io.github.marciomarinho.quotient.ledger.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document for the ledger query/admin API (served at {@code /swagger-ui.html} and {@code
 * /v3/api-docs}). The bearer security scheme reflects the Phase 9a design where these endpoints
 * validate Keycloak JWTs; today the tenant is bound from a header stand-in.
 */
@Configuration
public class OpenApiConfiguration {

  @Bean
  OpenAPI ledgerOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Quotient — Ledger API")
                .version("v1")
                .description("Tenant-scoped ledger balances, transactions, and admin operations."))
        .schemaRequirement(
            "bearerAuth",
            new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Keycloak-issued JWT (Phase 9a)"));
  }
}
