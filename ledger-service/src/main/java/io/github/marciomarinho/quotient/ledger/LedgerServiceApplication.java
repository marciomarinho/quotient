package io.github.marciomarinho.quotient.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the ledger service: double-entry ledger, invoicing, and the tenant-scoped query
 * API. See {@code docs/LEDGER_DESIGN.md} for the integrity model.
 */
@SpringBootApplication
public class LedgerServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(LedgerServiceApplication.class, args);
  }
}
