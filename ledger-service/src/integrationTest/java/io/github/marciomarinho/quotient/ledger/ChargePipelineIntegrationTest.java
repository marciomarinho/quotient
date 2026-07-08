package io.github.marciomarinho.quotient.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.marciomarinho.quotient.common.event.BillingWindow;
import io.github.marciomarinho.quotient.common.event.Charge;
import io.github.marciomarinho.quotient.common.event.EventJson;
import io.github.marciomarinho.quotient.common.money.Currency;
import io.github.marciomarinho.quotient.common.money.Money;
import io.github.marciomarinho.quotient.ledger.application.InvoiceService;
import io.github.marciomarinho.quotient.ledger.application.LedgerQueryService;
import io.github.marciomarinho.quotient.ledger.domain.AccountType;
import io.github.marciomarinho.quotient.ledger.domain.Invoice;
import io.github.marciomarinho.quotient.ledger.domain.InvoiceCreatedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Exercises the tail of the pipeline inside the ledger service: a charge on {@code
 * billing.charges.v1} is consumed and posted (double-entry + billed charge), then an invoice is
 * generated for the period, and the {@code invoice.created} event is relayed from the outbox to
 * Kafka.
 */
class ChargePipelineIntegrationTest extends AbstractLedgerIntegrationTest {

  private static final BillingWindow WINDOW =
      new BillingWindow(
          Instant.parse("2026-07-08T00:00:00Z"), Instant.parse("2026-07-08T00:01:00Z"));

  @Autowired LedgerQueryService queryService;
  @Autowired InvoiceService invoiceService;

  private final ObjectMapper mapper = EventJson.mapper();

  @Test
  void chargeIsPostedThenInvoicedAndTheEventIsRelayed() throws Exception {
    Charge charge =
        Charge.of(
            ACME,
            "llm.tokens.input",
            WINDOW,
            new TreeMap<>(),
            1_500_000,
            Money.ofMinor(40_000, Currency.AUD),
            1);

    // 1. Publish the charge; the ledger's consumer posts it.
    try (KafkaProducer<String, String> producer = producer()) {
      producer
          .send(
              new ProducerRecord<>(
                  "billing.charges.v1", ACME.asString(), mapper.writeValueAsString(charge)))
          .get();
    }

    // 2. Wait for the double-entry posting (RECEIVABLE = 40000 + 10% GST = 44000).
    awaitReceivable(ACME, 44_000L, Duration.ofSeconds(30));

    // 3. Generate the July 2026 invoice.
    Invoice invoice =
        invoiceService.generate(
            ACME, Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-08-01T00:00:00Z"));
    assertThat(invoice.net()).isEqualTo(Money.ofMinor(40_000, Currency.AUD));
    assertThat(invoice.tax()).isEqualTo(Money.ofMinor(4_000, Currency.AUD));
    assertThat(invoice.total()).isEqualTo(Money.ofMinor(44_000, Currency.AUD));
    assertThat(invoice.lines())
        .singleElement()
        .satisfies(
            line -> {
              assertThat(line.meterCode()).isEqualTo("llm.tokens.input");
              assertThat(line.quantity()).isEqualTo(1_500_000L);
            });

    // 4. The outbox relay publishes invoice.created for this invoice.
    InvoiceCreatedEvent event = awaitInvoiceCreated(invoice.id(), Duration.ofSeconds(30));
    assertThat(event.totalMinor()).isEqualTo(44_000L);
    assertThat(event.tenantId()).isEqualTo(ACME);
  }

  private void awaitReceivable(
      io.github.marciomarinho.quotient.common.tenant.TenantId tenant,
      long expected,
      Duration timeout)
      throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      long receivable =
          queryService.balances(tenant).stream()
              .filter(b -> b.type() == AccountType.RECEIVABLE)
              .mapToLong(b -> b.balance().amountMinor())
              .sum();
      if (receivable == expected) {
        return;
      }
      Thread.sleep(500);
    }
    throw new AssertionError("RECEIVABLE did not reach " + expected + " in time");
  }

  private InvoiceCreatedEvent awaitInvoiceCreated(UUID invoiceId, Duration timeout)
      throws Exception {
    long deadline = System.nanoTime() + timeout.toNanos();
    try (KafkaConsumer<String, String> consumer = consumer()) {
      consumer.subscribe(List.of("invoice.created.v1"));
      while (System.nanoTime() < deadline) {
        for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
          InvoiceCreatedEvent event = mapper.readValue(record.value(), InvoiceCreatedEvent.class);
          if (event.invoiceId().equals(invoiceId)) {
            return event;
          }
        }
      }
    }
    throw new AssertionError("invoice.created not relayed for " + invoiceId);
  }

  private KafkaProducer<String, String> producer() {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    return new KafkaProducer<>(props);
  }

  private KafkaConsumer<String, String> consumer() {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-invoice-" + UUID.randomUUID());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    return new KafkaConsumer<>(props);
  }
}
