package io.github.marciomarinho.quotient.ingest.reactive;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import io.restassured.http.ContentType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The ingestion API contract, exercised end-to-end against a real Kafka and Redis. Both gateway
 * implementations must satisfy this contract; Phase 8 extracts the shared assertions for the
 * reactive gateway.
 */
class IngestContractIntegrationTest extends AbstractGatewayIntegrationTest {

  private static final String ACME_KEY = demoKeys().get("acme");

  private static String eventJson(String key, String meter, long quantity, Instant occurredAt) {
    return """
        {"idempotencyKey":"%s","meterCode":"%s","quantity":%d,\
        "dimensions":{"model":"gpt-4o"},"occurredAt":"%s"}"""
        .formatted(key, meter, quantity, occurredAt);
  }

  private static Instant recentPast() {
    return Instant.now().minusSeconds(1);
  }

  @Test
  void rejectsRequestWithNoApiKey() {
    given()
        .contentType(ContentType.JSON)
        .body(eventJson("k", "llm.tokens.input", 10, recentPast()))
        .when()
        .post("/v1/usage/events")
        .then()
        .statusCode(401);
  }

  @Test
  void rejectsRequestWithInvalidApiKey() {
    given()
        .header("Authorization", "Bearer not-a-real-key")
        .contentType(ContentType.JSON)
        .body(eventJson("k", "llm.tokens.input", 10, recentPast()))
        .when()
        .post("/v1/usage/events")
        .then()
        .statusCode(401);
  }

  @Test
  void acceptsValidEventAndPublishesToKafka() {
    String key = "contract-accept-" + System.nanoTime();

    given()
        .header("Authorization", "Bearer " + ACME_KEY)
        .contentType(ContentType.JSON)
        .body(eventJson(key, "llm.tokens.input", 4200, recentPast()))
        .when()
        .post("/v1/usage/events")
        .then()
        .statusCode(202)
        .body("deduplicated", equalTo(false));

    List<String> published = drainUsageEvents(Duration.ofSeconds(30));
    assertThat(published)
        .anySatisfy(
            payload -> {
              assertThat(payload).contains(key);
              assertThat(payload).contains("llm.tokens.input");
              assertThat(payload).contains("11111111-1111-1111-1111-111111111111");
            });
  }

  @Test
  void replayedEventIsDeduplicated() {
    String key = "contract-dedup-" + System.nanoTime();
    String body = eventJson(key, "llm.tokens.input", 10, recentPast());

    given()
        .header("Authorization", "Bearer " + ACME_KEY)
        .contentType(ContentType.JSON)
        .body(body)
        .when()
        .post("/v1/usage/events")
        .then()
        .statusCode(202)
        .body("deduplicated", equalTo(false));

    given()
        .header("Authorization", "Bearer " + ACME_KEY)
        .contentType(ContentType.JSON)
        .body(body)
        .when()
        .post("/v1/usage/events")
        .then()
        .statusCode(202)
        .body("deduplicated", equalTo(true));
  }

  @Test
  void rejectsNegativeQuantityWith400() {
    given()
        .header("Authorization", "Bearer " + ACME_KEY)
        .contentType(ContentType.JSON)
        .body(eventJson("neg", "llm.tokens.input", -5, recentPast()))
        .when()
        .post("/v1/usage/events")
        .then()
        .statusCode(400);
  }

  @Test
  void rejectsUnknownMeterWith422() {
    given()
        .header("Authorization", "Bearer " + ACME_KEY)
        .contentType(ContentType.JSON)
        .body(eventJson("bad-meter", "no.such.meter", 10, recentPast()))
        .when()
        .post("/v1/usage/events")
        .then()
        .statusCode(422);
  }

  @Test
  void rejectsFutureTimestampWith422() {
    given()
        .header("Authorization", "Bearer " + ACME_KEY)
        .contentType(ContentType.JSON)
        .body(eventJson("future", "llm.tokens.input", 10, Instant.now().plusSeconds(600)))
        .when()
        .post("/v1/usage/events")
        .then()
        .statusCode(422);
  }

  @Test
  void batchEndpointAcceptsMultipleEvents() {
    long n = System.nanoTime();
    String batch =
        """
        {"events":[%s,%s]}"""
            .formatted(
                eventJson("batch-a-" + n, "llm.tokens.input", 1, recentPast()),
                eventJson("batch-b-" + n, "llm.tokens.output", 2, recentPast()));

    given()
        .header("Authorization", "Bearer " + ACME_KEY)
        .contentType(ContentType.JSON)
        .body(batch)
        .when()
        .post("/v1/usage/events:batch")
        .then()
        .statusCode(202)
        .body("received", equalTo(2))
        .body("accepted", equalTo(2));
  }
}
