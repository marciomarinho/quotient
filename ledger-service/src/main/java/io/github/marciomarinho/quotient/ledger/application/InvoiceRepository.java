package io.github.marciomarinho.quotient.ledger.application;

import io.github.marciomarinho.quotient.common.tenant.TenantId;
import io.github.marciomarinho.quotient.ledger.domain.BilledChargeRow;
import io.github.marciomarinho.quotient.ledger.domain.Invoice;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Persistence port for invoice generation and the outbox. */
public interface InvoiceRepository {

  /** Bind the tenant for the current transaction (RLS). */
  void bindTenant(TenantId tenantId);

  /** Uninvoiced charges whose window falls within {@code [start, end)} for the tenant. */
  List<BilledChargeRow> uninvoicedCharges(TenantId tenantId, Instant start, Instant end);

  /**
   * Persist an invoice atomically: the invoice row, its lines, linking the given charges to it, and
   * an {@code invoice.created} outbox row — all in the caller's transaction so the event can never
   * be lost relative to the invoice.
   */
  void save(Invoice invoice, List<UUID> chargeIds, String outboxPayload);
}
