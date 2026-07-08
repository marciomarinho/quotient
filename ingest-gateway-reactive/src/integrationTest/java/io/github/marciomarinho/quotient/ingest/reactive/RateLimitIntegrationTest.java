package io.github.marciomarinho.quotient.ingest.reactive;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Verifies the per-tenant token bucket: with a capacity of 2 and no meaningful refill during the
 * test, the third request from a tenant is rejected with 429 — the noisy-neighbour protection.
 */
@TestPropertySource(
    properties = {
      "quotient.ingest.rate-limit.capacity=2",
      "quotient.ingest.rate-limit.refill-tokens=2",
      "quotient.ingest.rate-limit.refill-period=60s"
    })
class RateLimitIntegrationTest extends AbstractGatewayIntegrationTest {

  private static final String ACME_KEY = demoKeys().get("acme");

  private static String eventJson(String key) {
    return """
        {"idempotencyKey":"%s","meterCode":"llm.tokens.input","quantity":1,\
        "dimensions":{},"occurredAt":"%s"}"""
        .formatted(key, Instant.now().minusSeconds(1));
  }

  private void post(String key, int expectedStatus) {
    given()
        .header("Authorization", "Bearer " + ACME_KEY)
        .contentType(ContentType.JSON)
        .body(eventJson(key))
        .when()
        .post("/v1/usage/events")
        .then()
        .statusCode(expectedStatus);
  }

  @Test
  void thirdRequestIsRateLimited() {
    long n = System.nanoTime();
    post("rl-a-" + n, 202);
    post("rl-b-" + n, 202);
    post("rl-c-" + n, 429);
  }
}
