package io.github.marciomarinho.quotient.ledger.adapter.persistence;

import io.github.marciomarinho.quotient.common.money.Currency;
import io.github.marciomarinho.quotient.common.money.Money;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import io.github.marciomarinho.quotient.ledger.application.InvoiceRepository;
import io.github.marciomarinho.quotient.ledger.domain.BilledChargeRow;
import io.github.marciomarinho.quotient.ledger.domain.Invoice;
import io.github.marciomarinho.quotient.ledger.domain.Invoice.InvoiceLine;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** JDBC adapter for invoice persistence + the transactional outbox. */
@Repository
public class JdbcInvoiceRepository implements InvoiceRepository {

  private final JdbcClient jdbc;

  public JdbcInvoiceRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void bindTenant(TenantId tenantId) {
    jdbc.sql("SELECT set_config('app.tenant_id', ?, true)")
        .param(tenantId.asString())
        .query(String.class)
        .single();
  }

  @Override
  public List<BilledChargeRow> uninvoicedCharges(TenantId tenantId, Instant start, Instant end) {
    bindTenant(tenantId);
    return jdbc.sql(
            "SELECT charge_id, meter_code, quantity_billed, amount_minor, currency "
                + "FROM billed_charge "
                + "WHERE invoice_id IS NULL AND window_start >= ? AND window_start < ? "
                + "ORDER BY meter_code, window_start")
        .param(at(start))
        .param(at(end))
        .query(
            (rs, n) ->
                new BilledChargeRow(
                    rs.getObject("charge_id", UUID.class),
                    rs.getString("meter_code"),
                    rs.getLong("quantity_billed"),
                    Money.ofMinor(
                        rs.getLong("amount_minor"), Currency.valueOf(rs.getString("currency")))))
        .list();
  }

  @Override
  public void save(Invoice invoice, List<UUID> chargeIds, String outboxPayload) {
    bindTenant(invoice.tenantId());

    jdbc.sql(
            "INSERT INTO invoice (id, tenant_id, period_start, period_end, currency, "
                + "total_minor, tax_minor, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
        .param(invoice.id())
        .param(invoice.tenantId().value())
        .param(at(invoice.periodStart()))
        .param(at(invoice.periodEnd()))
        .param(invoice.total().currency().name())
        .param(invoice.total().amountMinor())
        .param(invoice.tax().amountMinor())
        .param(invoice.status())
        .update();

    for (InvoiceLine line : invoice.lines()) {
      jdbc.sql(
              "INSERT INTO invoice_line (invoice_id, tenant_id, meter_code, quantity, "
                  + "amount_minor, currency) VALUES (?, ?, ?, ?, ?, ?)")
          .param(invoice.id())
          .param(invoice.tenantId().value())
          .param(line.meterCode())
          .param(line.quantity())
          .param(line.amount().amountMinor())
          .param(line.amount().currency().name())
          .update();
    }

    for (UUID chargeId : chargeIds) {
      jdbc.sql("UPDATE billed_charge SET invoice_id = ? WHERE charge_id = ?")
          .param(invoice.id())
          .param(chargeId)
          .update();
    }

    jdbc.sql(
            "INSERT INTO outbox (id, tenant_id, aggregate_type, aggregate_id, event_type, payload) "
                + "VALUES (?, ?, 'invoice', ?, 'invoice.created', ?::jsonb)")
        .param(UUID.randomUUID())
        .param(invoice.tenantId().value())
        .param(invoice.id())
        .param(outboxPayload)
        .update();
  }

  private static OffsetDateTime at(Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }
}
