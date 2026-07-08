package io.github.marciomarinho.quotient.ledger;

import io.github.marciomarinho.quotient.common.event.BillingWindow;
import io.github.marciomarinho.quotient.common.event.Charge;
import io.github.marciomarinho.quotient.common.money.Currency;
import io.github.marciomarinho.quotient.common.money.Money;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for ledger integration tests. Starts a single Postgres 17 container for the whole suite and
 * wires two credentials: Flyway migrates as the container superuser (schema owner), while the
 * application connects as the least- privilege {@code quotient_app} role created by the migrations
 * — which is what makes RLS and the append-only grants actually apply in these tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public abstract class AbstractLedgerIntegrationTest {

  // Canonical demo tenants (also seeded by V4).
  protected static final TenantId ACME =
      TenantId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
  protected static final TenantId GLOBEX =
      TenantId.of(UUID.fromString("22222222-2222-2222-2222-222222222222"));

  @SuppressWarnings("resource")
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17")
          .withDatabaseName("quotient")
          .withUsername("owner")
          .withPassword("owner");

  static {
    POSTGRES.start();
  }

  /**
   * The container is shared across the whole suite for speed, so reset the ledger data before each
   * test to keep balances deterministic. This runs as the container owner (bypassing RLS, with
   * TRUNCATE rights); seeded tenants are preserved. Superclass {@code @BeforeEach} runs before any
   * subclass one, so subclass seeding lands on a clean slate.
   */
  @BeforeEach
  void resetLedgerData() throws SQLException {
    try (Connection connection = POSTGRES.createConnection("");
        var statement = connection.createStatement()) {
      statement.execute(
          "TRUNCATE ledger_entry, ledger_transaction, ledger_account, account_balance, "
              + "invoice_line, invoice, outbox RESTART IDENTITY CASCADE");
    }
  }

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    // App runs as quotient_app (RLS applies).
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", () -> "quotient_app");
    registry.add("spring.datasource.password", () -> "quotient_app");
    // Flyway runs as the owner/superuser to create roles + schema + policies.
    registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
    registry.add("spring.flyway.user", POSTGRES::getUsername);
    registry.add("spring.flyway.password", POSTGRES::getPassword);
  }

  /** A charge for {@code tenant} with the given net revenue, in a stable window. */
  protected static Charge charge(TenantId tenant, long revenueMinor, String meterCode) {
    Instant start = Instant.parse("2026-07-08T00:00:00Z");
    return Charge.of(
        tenant,
        meterCode,
        new BillingWindow(start, start.plus(1, ChronoUnit.MINUTES)),
        new TreeMap<>(),
        4200,
        Money.ofMinor(revenueMinor, Currency.AUD),
        1);
  }
}
