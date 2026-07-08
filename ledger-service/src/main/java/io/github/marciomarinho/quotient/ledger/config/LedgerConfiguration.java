package io.github.marciomarinho.quotient.ledger.config;

import io.github.marciomarinho.quotient.ledger.domain.DoubleEntryPosting;
import io.github.marciomarinho.quotient.ledger.domain.GstPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wires the framework-free ledger domain services as Spring beans. Keeping this in one small
 * configuration class means the domain classes themselves stay free of Spring annotations
 * (constructor injection only, no {@code @Component} on domain types).
 */
@Configuration
@EnableScheduling
public class LedgerConfiguration {

  @Bean
  GstPolicy gstPolicy() {
    return GstPolicy.AUSTRALIA_GST;
  }

  @Bean
  DoubleEntryPosting doubleEntryPosting(GstPolicy gstPolicy) {
    return new DoubleEntryPosting(gstPolicy);
  }
}
