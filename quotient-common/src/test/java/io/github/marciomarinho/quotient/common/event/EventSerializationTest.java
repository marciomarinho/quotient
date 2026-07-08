package io.github.marciomarinho.quotient.common.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.marciomarinho.quotient.common.money.Currency;
import io.github.marciomarinho.quotient.common.money.Money;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Round-trip and wire-shape tests for the event contracts. */
class EventSerializationTest {

  private final ObjectMapper mapper = EventJson.mapper();

  private static final TenantId TENANT =
      TenantId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));

  @Test
  void usageEvent_roundTripsAndKeepsTenantIdFlat() throws Exception {
    Map<String, String> dims = new LinkedHashMap<>();
    dims.put("model", "gpt-4o");
    dims.put("region", "ap-southeast-2");
    UsageEvent event =
        UsageEvent.received(
            "idem-123",
            TENANT,
            "llm.tokens.input",
            4200,
            dims,
            Instant.parse("2026-07-08T00:00:00Z"),
            Instant.parse("2026-07-08T00:00:01Z"));

    String json = mapper.writeValueAsString(event);
    JsonNode node = mapper.readTree(json);

    assertThat(node.get("tenantId").asText())
        .as("tenantId serializes as a flat UUID string")
        .isEqualTo("11111111-1111-1111-1111-111111111111");
    assertThat(node.get("schemaVersion").asInt()).isEqualTo(UsageEvent.CURRENT_SCHEMA_VERSION);

    UsageEvent back = mapper.readValue(json, UsageEvent.class);
    assertThat(back).isEqualTo(event);
  }

  @Test
  void usageEvent_dimensionsKeyIsStableRegardlessOfInsertionOrder() {
    Map<String, String> a = new LinkedHashMap<>();
    a.put("region", "ap-southeast-2");
    a.put("model", "gpt-4o");
    Map<String, String> b = new LinkedHashMap<>();
    b.put("model", "gpt-4o");
    b.put("region", "ap-southeast-2");

    UsageEvent ea =
        UsageEvent.received("k", TENANT, "m", 1, a, Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
    UsageEvent eb =
        UsageEvent.received("k", TENANT, "m", 1, b, Instant.EPOCH, Instant.EPOCH.plusSeconds(1));

    assertThat(ea.dimensionsKey()).isEqualTo("model=gpt-4o;region=ap-southeast-2");
    assertThat(ea.dimensionsKey()).isEqualTo(eb.dimensionsKey());
  }

  @Test
  void charge_roundTripsAndMoneyIsExact() throws Exception {
    TreeMap<String, String> dims = new TreeMap<>(Map.of("model", "gpt-4o"));
    Charge charge =
        Charge.of(
            TENANT,
            "llm.tokens.input",
            new BillingWindow(
                Instant.parse("2026-07-08T00:00:00Z"), Instant.parse("2026-07-08T00:01:00Z")),
            dims,
            4200,
            Money.ofMinor(126, Currency.AUD),
            7);

    String json = mapper.writeValueAsString(charge);
    Charge back = mapper.readValue(json, Charge.class);

    assertThat(back).isEqualTo(charge);
    assertThat(back.amount()).isEqualTo(Money.ofMinor(126, Currency.AUD));
  }

  @Test
  void charge_deterministicIdIsStableForSameCoordinates() {
    BillingWindow window =
        new BillingWindow(
            Instant.parse("2026-07-08T00:00:00Z"), Instant.parse("2026-07-08T00:01:00Z"));
    TreeMap<String, String> dims = new TreeMap<>(Map.of("model", "gpt-4o"));

    Charge first = Charge.of(TENANT, "m", window, dims, 10, Money.ofMinor(100, Currency.AUD), 3);
    Charge second = Charge.of(TENANT, "m", window, dims, 10, Money.ofMinor(100, Currency.AUD), 3);
    Charge differentPlan =
        Charge.of(TENANT, "m", window, dims, 10, Money.ofMinor(100, Currency.AUD), 4);

    assertThat(first.chargeId())
        .as("same coordinates + plan version => same deterministic id")
        .isEqualTo(second.chargeId());
    assertThat(first.chargeId())
        .as("a different plan version must produce a different charge id")
        .isNotEqualTo(differentPlan.chargeId());
  }
}
