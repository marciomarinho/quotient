package io.github.marciomarinho.quotient.ingest.vthreads;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the virtual-threads ingestion gateway (Spring MVC + Project Loom). Externally
 * identical to the reactive gateway; see Section 7 of the project plan and {@code
 * docs/BENCHMARK.md}.
 */
@SpringBootApplication
public class IngestVthreadsApplication {

  public static void main(String[] args) {
    SpringApplication.run(IngestVthreadsApplication.class, args);
  }
}
