package io.github.marciomarinho.quotient.rating.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

/** Enables the Kafka listener infrastructure and binds the rating configuration. */
@Configuration
@EnableKafka
@EnableConfigurationProperties(RatingProperties.class)
public class RatingConfiguration {}
