package io.github.marciomarinho.quotient.ledger.adapter.web;

import io.github.marciomarinho.quotient.common.tenant.TenantId;
import io.github.marciomarinho.quotient.ledger.application.InvoiceService;
import io.github.marciomarinho.quotient.ledger.domain.Invoice;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Invoice generation. Aggregates a tenant's charges for a period into an invoice and enqueues the
 * {@code invoice.created} event (via the outbox). In Phase 9a this is restricted to the
 * tenant-admin/operator roles.
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/invoices")
public class InvoiceController {

  private final InvoiceService invoiceService;

  public InvoiceController(InvoiceService invoiceService) {
    this.invoiceService = invoiceService;
  }

  @PostMapping
  @Operation(summary = "Generate the invoice for a tenant and billing period (YYYY-MM)")
  public ResponseEntity<InvoiceResponse> generate(
      @PathVariable UUID tenantId, @RequestParam String period) {
    YearMonth month = YearMonth.parse(period);
    Instant start = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant end = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

    Invoice invoice = invoiceService.generate(TenantId.of(tenantId), start, end);
    return ResponseEntity.status(HttpStatus.CREATED).body(InvoiceResponse.from(invoice));
  }

  /** Response DTO for a generated invoice. */
  record InvoiceResponse(
      UUID id,
      String tenantId,
      Instant periodStart,
      Instant periodEnd,
      String currency,
      long netMinor,
      long taxMinor,
      long totalMinor,
      String status,
      List<LineResponse> lines) {

    static InvoiceResponse from(Invoice invoice) {
      return new InvoiceResponse(
          invoice.id(),
          invoice.tenantId().asString(),
          invoice.periodStart(),
          invoice.periodEnd(),
          invoice.total().currency().name(),
          invoice.net().amountMinor(),
          invoice.tax().amountMinor(),
          invoice.total().amountMinor(),
          invoice.status(),
          invoice.lines().stream()
              .map(l -> new LineResponse(l.meterCode(), l.quantity(), l.amount().amountMinor()))
              .toList());
    }

    record LineResponse(String meterCode, long quantity, long amountMinor) {}
  }
}
