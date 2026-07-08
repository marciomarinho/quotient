package io.github.marciomarinho.quotient.ledger.cli;

import io.github.marciomarinho.quotient.ledger.application.LedgerVerifier;
import io.github.marciomarinho.quotient.ledger.application.VerificationReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Runs the ledger verification pass and exits the process with a non-zero code if any tenant's
 * materialized balances have drifted from the balances recomputed from raw entries. Activated by
 * {@code --ledger.verify.enabled=true} (the {@code ledgerVerify} Gradle task / {@code make
 * ledger-verify}); otherwise absent, so normal service startup is unaffected.
 */
@Component
@ConditionalOnProperty(name = "ledger.verify.enabled", havingValue = "true")
public class LedgerVerifyRunner implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(LedgerVerifyRunner.class);

  private final LedgerVerifier verifier;
  private final ConfigurableApplicationContext context;

  public LedgerVerifyRunner(LedgerVerifier verifier, ConfigurableApplicationContext context) {
    this.verifier = verifier;
    this.context = context;
  }

  @Override
  public void run(ApplicationArguments args) {
    VerificationReport report = verifier.verifyAll();
    report
        .tenants()
        .forEach(
            t ->
                LOG.info(
                    "tenant {} : {}{}",
                    t.tenantId(),
                    t.match() ? "OK" : "DRIFT",
                    t.match() ? "" : " " + t.discrepancies()));

    int exitCode;
    if (report.allMatch()) {
      LOG.info("ledger verify: all {} tenant(s) balanced", report.tenants().size());
      exitCode = 0;
    } else {
      LOG.error("ledger verify: DRIFT DETECTED in one or more tenants");
      exitCode = 2;
    }
    System.exit(SpringApplication.exit(context, () -> exitCode));
  }
}
