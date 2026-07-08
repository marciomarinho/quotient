package io.github.marciomarinho.quotient.aggregator.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;

/**
 * Enables Kafka Streams for the aggregator. The Streams runtime config (application id, {@code
 * processing.guarantee=exactly_once_v2}, bootstrap) comes from {@code spring.kafka.streams.*} in
 * application.yml; the topology itself is wired by {@link AggregatorPipeline}.
 */
@Configuration
@EnableKafkaStreams
@EnableConfigurationProperties(AggregatorProperties.class)
public class AggregatorConfiguration {}
