package io.github.marciomarinho.quotient.ingest.reactive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the reactive ingestion gateway (Spring WebFlux + reactor-kafka). Externally
 * identical to the vthreads gateway; see Section 7 of the project plan and {@code
 * docs/BENCHMARK.md}.
 */
@SpringBootApplication
public class IngestReactiveApplication {

  public static void main(String[] args) {
    SpringApplication.run(IngestReactiveApplication.class, args);
  }
}
