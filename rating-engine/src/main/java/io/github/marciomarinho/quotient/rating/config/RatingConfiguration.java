package io.github.marciomarinho.quotient.rating.config;

import io.github.marciomarinho.quotient.common.event.BillingWindow;
import io.github.marciomarinho.quotient.common.event.Charge;
import io.github.marciomarinho.quotient.common.event.MeterReading;
import io.github.marciomarinho.quotient.common.meter.Aggregation;
import io.github.marciomarinho.quotient.common.money.Currency;
import io.github.marciomarinho.quotient.common.money.Money;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Enables the Kafka listener infrastructure and binds the rating configuration.
 *
 * <p>{@link RegisterReflectionForBinding} gives the GraalVM native image the reflection metadata
 * Jackson needs to (de)serialize the event records that are read/written manually (the consumed
 * {@link MeterReading} and the produced {@link Charge}, plus their nested value types). Framework
 * types (kafka-clients, Jackson, Spring) come from the GraalVM reachability-metadata repository.
 */
@Configuration
@EnableKafka
@EnableConfigurationProperties(RatingProperties.class)
@RegisterReflectionForBinding({
  MeterReading.class,
  Charge.class,
  BillingWindow.class,
  Money.class,
  Currency.class,
  TenantId.class,
  Aggregation.class
})
public class RatingConfiguration {}
