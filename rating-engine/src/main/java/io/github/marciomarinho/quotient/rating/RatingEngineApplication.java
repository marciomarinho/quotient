package io.github.marciomarinho.quotient.rating;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the rating engine. Consumes aggregates, applies versioned per-tenant pricing
 * plans, and emits Charge records. Deterministic by design; see Section 9 of the project plan.
 */
@SpringBootApplication
public class RatingEngineApplication {

  public static void main(String[] args) {
    SpringApplication.run(RatingEngineApplication.class, args);
  }
}
