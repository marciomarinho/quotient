package io.github.marciomarinho.quotient.aggregator.config;

import io.github.marciomarinho.quotient.aggregator.topology.MeterAggregationTopology;
import org.apache.kafka.streams.StreamsBuilder;
import org.springframework.stereotype.Component;

/**
 * Wires the meter-aggregation topology onto the Spring-managed {@link StreamsBuilder} at startup.
 * Kept as a small component (rather than an {@code @Autowired} method on the config class) so there
 * is no self-referential bean and the topology class itself stays a plain, unit-testable object.
 */
@Component
public class AggregatorPipeline {

  public AggregatorPipeline(StreamsBuilder streamsBuilder, AggregatorProperties properties) {
    new MeterAggregationTopology(properties).build(streamsBuilder);
  }
}
