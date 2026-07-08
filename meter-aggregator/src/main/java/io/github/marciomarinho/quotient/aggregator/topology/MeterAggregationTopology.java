package io.github.marciomarinho.quotient.aggregator.topology;

import io.github.marciomarinho.quotient.aggregator.config.AggregatorProperties;
import io.github.marciomarinho.quotient.common.event.MeterReading;
import io.github.marciomarinho.quotient.common.event.UsageEvent;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.Suppressed.BufferConfig;
import org.apache.kafka.streams.kstream.TimeWindows;

/**
 * Builds the Kafka Streams topology that turns raw usage events into windowed meter readings.
 *
 * <pre>
 *   usage.events.v1
 *     -> group by (tenant | meter | dimensions)
 *     -> tumbling window (size, grace)
 *     -> aggregate per the meter's function (SUM/COUNT/MAX)
 *     -> suppress until the window closes (emit one final reading)
 *     -> usage.aggregates.v1  (keyed by tenant)
 * </pre>
 *
 * <p>Exactly-once is configured at the Streams level (processing.guarantee = exactly_once_v2). The
 * topology itself is pure and side-effect-free, so it can be exercised with the TopologyTestDriver
 * as well as against a real broker.
 */
public class MeterAggregationTopology {

  private static final String STORE_NAME = "meter-aggregate-store";

  private final AggregatorProperties properties;

  public MeterAggregationTopology(AggregatorProperties properties) {
    this.properties = properties;
  }

  /** Wire the aggregation flow onto {@code builder}. */
  public void build(StreamsBuilder builder) {
    JsonSerde<UsageEvent> eventSerde = new JsonSerde<>(UsageEvent.class);
    JsonSerde<MeterAggregate> aggregateSerde = new JsonSerde<>(MeterAggregate.class);
    JsonSerde<MeterReading> readingSerde = new JsonSerde<>(MeterReading.class);

    builder.stream(properties.inputTopic(), Consumed.with(Serdes.String(), eventSerde))
        .groupBy((key, event) -> groupingKey(event), Grouped.with(Serdes.String(), eventSerde))
        .windowedBy(TimeWindows.ofSizeAndGrace(properties.windowSize(), properties.grace()))
        .aggregate(
            MeterAggregate::empty,
            (key, event, aggregate) ->
                aggregate.add(event, properties.aggregationFor(event.meterCode())),
            Materialized.<String, MeterAggregate>as(
                    org.apache.kafka.streams.state.Stores.persistentWindowStore(
                        STORE_NAME,
                        properties.windowSize().plus(properties.grace()).multipliedBy(2),
                        properties.windowSize(),
                        false))
                .withKeySerde(Serdes.String())
                .withValueSerde(aggregateSerde))
        .suppress(Suppressed.untilWindowCloses(BufferConfig.unbounded()))
        .toStream()
        .map((windowedKey, aggregate) -> toReading(windowedKey.window(), aggregate))
        .to(properties.outputTopic(), Produced.with(Serdes.String(), readingSerde));
  }

  private static String groupingKey(UsageEvent event) {
    return event.tenantId().asString() + '|' + event.meterCode() + '|' + event.dimensionsKey();
  }

  private static KeyValue<String, MeterReading> toReading(
      org.apache.kafka.streams.kstream.Window window, MeterAggregate aggregate) {
    MeterReading reading =
        MeterReading.of(
            TenantId.fromString(aggregate.tenantId()),
            aggregate.meterCode(),
            new io.github.marciomarinho.quotient.common.event.BillingWindow(
                window.startTime(), window.endTime()),
            aggregate.value(),
            aggregate.eventCount(),
            aggregate.dimensions());
    return KeyValue.pair(aggregate.tenantId(), reading);
  }
}
