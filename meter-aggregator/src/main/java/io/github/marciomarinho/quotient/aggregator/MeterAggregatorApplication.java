package io.github.marciomarinho.quotient.aggregator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the meter aggregator (Kafka Streams). Windows raw usage events into billable
 * meter readings with exactly-once semantics. See Section 8 of the project plan and {@code
 * docs/EXACTLY_ONCE.md}.
 */
@SpringBootApplication
public class MeterAggregatorApplication {

  public static void main(String[] args) {
    SpringApplication.run(MeterAggregatorApplication.class, args);
  }
}
