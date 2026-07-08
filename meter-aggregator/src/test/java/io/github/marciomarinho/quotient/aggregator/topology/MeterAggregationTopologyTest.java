package io.github.marciomarinho.quotient.aggregator.topology;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.marciomarinho.quotient.aggregator.config.AggregatorProperties;
import io.github.marciomarinho.quotient.common.event.MeterReading;
import io.github.marciomarinho.quotient.common.event.UsageEvent;
import io.github.marciomarinho.quotient.common.meter.Aggregation;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Deterministic tests of the aggregation topology using the TopologyTestDriver — no broker. Stream
 * time is advanced by feeding a record beyond the window + grace, which is what makes the
 * suppressed window emit its final reading.
 */
class MeterAggregationTopologyTest {

  private static final String IN = "usage.events.v1";
  private static final String OUT = "usage.aggregates.v1";
  private static final TenantId ACME =
      TenantId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
  private static final Instant WINDOW_BASE = Instant.parse("2026-07-08T00:00:00Z");

  private TopologyTestDriver driver;
  private TestInputTopic<String, UsageEvent> input;
  private TestOutputTopic<String, MeterReading> output;

  @BeforeEach
  void setUp() {
    AggregatorProperties props =
        new AggregatorProperties(
            IN,
            OUT,
            Duration.ofMinutes(1),
            Duration.ofSeconds(30),
            Map.of("llm.requests", Aggregation.COUNT));
    StreamsBuilder builder = new StreamsBuilder();
    new MeterAggregationTopology(props).build(builder);
    Topology topology = builder.build();

    Properties config = new Properties();
    config.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-aggregator");
    config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
    config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());

    driver = new TopologyTestDriver(topology, config);
    input =
        driver.createInputTopic(
            IN, new StringSerializer(), new JsonSerde<>(UsageEvent.class).serializer());
    output =
        driver.createOutputTopic(
            OUT, new StringDeserializer(), new JsonSerde<>(MeterReading.class).deserializer());
  }

  @AfterEach
  void tearDown() {
    driver.close();
  }

  private void send(String meter, long quantity, Map<String, String> dims, Instant at) {
    UsageEvent event =
        UsageEvent.received(
            "idem-" + at.toEpochMilli() + '-' + quantity, ACME, meter, quantity, dims, at, at);
    input.pipeInput(ACME.asString(), event, at);
  }

  /** Advance stream time past window+grace so the first window is emitted. */
  private void closeFirstWindow() {
    send("zzz.trigger", 0, Map.of(), WINDOW_BASE.plus(Duration.ofMinutes(2)));
  }

  @Test
  void sumMeterAddsQuantitiesInTheWindow() {
    send("llm.tokens.input", 100, Map.of("model", "gpt-4o"), WINDOW_BASE.plusSeconds(10));
    send("llm.tokens.input", 200, Map.of("model", "gpt-4o"), WINDOW_BASE.plusSeconds(20));
    send("llm.tokens.input", 300, Map.of("model", "gpt-4o"), WINDOW_BASE.plusSeconds(30));
    closeFirstWindow();

    MeterReading reading = readingFor("llm.tokens.input");
    assertThat(reading.quantity()).as("SUM of 100+200+300").isEqualTo(600L);
    assertThat(reading.eventCount()).isEqualTo(3L);
    assertThat(reading.window().start()).isEqualTo(WINDOW_BASE);
    assertThat(reading.window().end()).isEqualTo(WINDOW_BASE.plus(Duration.ofMinutes(1)));
  }

  @Test
  void countMeterCountsEventsNotQuantities() {
    for (int i = 0; i < 5; i++) {
      send("llm.requests", 999, Map.of(), WINDOW_BASE.plusSeconds(i));
    }
    closeFirstWindow();

    MeterReading reading = readingFor("llm.requests");
    assertThat(reading.quantity()).as("COUNT reports 5 events, not the quantity").isEqualTo(5L);
    assertThat(reading.eventCount()).isEqualTo(5L);
  }

  @Test
  void lateEventWithinGraceIsIncluded() {
    send("llm.tokens.input", 100, Map.of(), WINDOW_BASE.plusSeconds(10));
    // Arrives after the window end (01:00) but within the 30s grace: still counted.
    send("llm.tokens.input", 50, Map.of(), WINDOW_BASE.plusSeconds(50));
    closeFirstWindow();

    assertThat(readingFor("llm.tokens.input").quantity()).isEqualTo(150L);
  }

  @Test
  void differentDimensionsProduceSeparateReadings() {
    send("llm.tokens.input", 100, Map.of("model", "gpt-4o"), WINDOW_BASE.plusSeconds(10));
    send("llm.tokens.input", 70, Map.of("model", "claude"), WINDOW_BASE.plusSeconds(20));
    closeFirstWindow();

    List<MeterReading> readings =
        output.readValuesToList().stream()
            .filter(r -> r.meterCode().equals("llm.tokens.input"))
            .toList();
    assertThat(readings).hasSize(2);
    assertThat(readings).extracting(MeterReading::quantity).containsExactlyInAnyOrder(100L, 70L);
  }

  private MeterReading readingFor(String meterCode) {
    return output.readValuesToList().stream()
        .filter(r -> r.meterCode().equals(meterCode))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no reading emitted for " + meterCode));
  }
}
