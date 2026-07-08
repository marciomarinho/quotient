package io.github.marciomarinho.quotient.ledger.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.marciomarinho.quotient.common.event.EventJson;
import io.github.marciomarinho.quotient.common.money.Currency;
import io.github.marciomarinho.quotient.common.money.Money;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import io.github.marciomarinho.quotient.ledger.domain.BilledChargeRow;
import io.github.marciomarinho.quotient.ledger.domain.GstPolicy;
import io.github.marciomarinho.quotient.ledger.domain.Invoice;
import io.github.marciomarinho.quotient.ledger.domain.Invoice.InvoiceLine;
import io.github.marciomarinho.quotient.ledger.domain.InvoiceCreatedEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates a tenant's invoice for a period from the charges the ledger has already posted, and
 * enqueues an {@code invoice.created} event in the same transaction (outbox pattern).
 *
 * <p>Runs at SERIALIZABLE isolation so two concurrent generations cannot both claim the same
 * uninvoiced charges. The invoice does not post revenue — that already happened per charge; it is
 * the periodic statement over those charges, so a tenant's invoiced totals always reconcile with
 * its ledger.
 */
@Service
public class InvoiceService {

  private final InvoiceRepository repository;
  private final GstPolicy gstPolicy;
  private final ObjectMapper mapper = EventJson.mapper();

  public InvoiceService(InvoiceRepository repository, GstPolicy gstPolicy) {
    this.repository = repository;
    this.gstPolicy = gstPolicy;
  }

  /** Generate the invoice for {@code tenant} over {@code [start, end)}. */
  @Transactional(isolation = Isolation.SERIALIZABLE)
  public Invoice generate(TenantId tenant, Instant start, Instant end) {
    List<BilledChargeRow> charges = repository.uninvoicedCharges(tenant, start, end);

    List<InvoiceLine> lines = toLines(charges);
    Money net =
        lines.stream().map(InvoiceLine::amount).reduce(Money.zero(Currency.AUD), Money::plus);
    Money tax = gstPolicy.on(net);
    Money total = net.plus(tax);

    Invoice invoice =
        new Invoice(UUID.randomUUID(), tenant, start, end, net, tax, total, lines, "GENERATED");
    List<UUID> chargeIds = charges.stream().map(BilledChargeRow::chargeId).toList();

    repository.save(invoice, chargeIds, serialize(invoice));
    return invoice;
  }

  private static List<InvoiceLine> toLines(List<BilledChargeRow> charges) {
    // Preserve a stable, meter-ordered set of lines.
    Map<String, InvoiceLine> byMeter = new LinkedHashMap<>();
    for (BilledChargeRow charge : charges) {
      byMeter.merge(
          charge.meterCode(),
          new InvoiceLine(charge.meterCode(), charge.quantity(), charge.amount()),
          (existing, add) ->
              new InvoiceLine(
                  existing.meterCode(),
                  existing.quantity() + add.quantity(),
                  existing.amount().plus(add.amount())));
    }
    return new ArrayList<>(byMeter.values());
  }

  private String serialize(Invoice invoice) {
    try {
      return mapper.writeValueAsString(InvoiceCreatedEvent.from(invoice));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to serialize invoice.created event", e);
    }
  }
}
