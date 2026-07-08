package io.github.marciomarinho.quotient.aggregator.config;

import io.github.marciomarinho.quotient.common.meter.Aggregation;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the meter aggregator, bound from {@code quotient.aggregator.*}.
 *
 * @param inputTopic raw usage events in
 * @param outputTopic meter readings out
 * @param windowSize tumbling window length
 * @param grace how long after a window closes late events are still folded in
 * @param meters per-meter aggregation function; meters not listed default to SUM
 */
@ConfigurationProperties(prefix = "quotient.aggregator")
public record AggregatorProperties(
    @DefaultValue("usage.events.v1") String inputTopic,
    @DefaultValue("usage.aggregates.v1") String outputTopic,
    @DefaultValue("1m") Duration windowSize,
    @DefaultValue("30s") Duration grace,
    Map<String, Aggregation> meters) {

  /** The aggregation for {@code meterCode}, defaulting to SUM. */
  public Aggregation aggregationFor(String meterCode) {
    if (meters == null) {
      return Aggregation.SUM;
    }
    return meters.getOrDefault(meterCode, Aggregation.SUM);
  }
}
