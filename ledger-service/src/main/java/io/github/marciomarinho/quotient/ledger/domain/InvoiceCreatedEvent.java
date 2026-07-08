package io.github.marciomarinho.quotient.ledger.domain;

import io.github.marciomarinho.quotient.common.tenant.TenantId;
import java.time.Instant;
import java.util.UUID;

/**
 * The {@code invoice.created} event, written to the outbox in the same transaction as the invoice
 * and later relayed to Kafka. Carries the roll-up so consumers need not re-read the ledger.
 */
public record InvoiceCreatedEvent(
    int schemaVersion,
    UUID invoiceId,
    TenantId tenantId,
    Instant periodStart,
    Instant periodEnd,
    long netMinor,
    long taxMinor,
    long totalMinor,
    String currency) {

  public static final int CURRENT_SCHEMA_VERSION = 1;

  public static InvoiceCreatedEvent from(Invoice invoice) {
    return new InvoiceCreatedEvent(
        CURRENT_SCHEMA_VERSION,
        invoice.id(),
        invoice.tenantId(),
        invoice.periodStart(),
        invoice.periodEnd(),
        invoice.net().amountMinor(),
        invoice.tax().amountMinor(),
        invoice.total().amountMinor(),
        invoice.total().currency().name());
  }
}
