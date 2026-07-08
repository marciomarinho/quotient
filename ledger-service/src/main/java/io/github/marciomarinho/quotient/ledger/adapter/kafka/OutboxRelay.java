package io.github.marciomarinho.quotient.ledger.adapter.kafka;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drains the transactional outbox: publishes unsent rows to Kafka and stamps {@code published_at}.
 * This is the second half of the outbox pattern — the invoice and its {@code invoice.created} row
 * are written atomically by {@link
 * io.github.marciomarinho.quotient.ledger.application.InvoiceService}, and this relay guarantees
 * the event eventually reaches Kafka (at-least-once; consumers are idempotent). The outbox is not
 * RLS-scoped so the relay sees every tenant's pending events.
 */
@Component
public class OutboxRelay {

  private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);
  private static final int BATCH = 100;

  private final JdbcClient jdbc;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final String topic;

  public OutboxRelay(
      JdbcClient jdbc,
      KafkaTemplate<String, String> kafkaTemplate,
      @Value("${quotient.ledger.invoice-created-topic:invoice.created.v1}") String topic) {
    this.jdbc = jdbc;
    this.kafkaTemplate = kafkaTemplate;
    this.topic = topic;
  }

  @Scheduled(fixedDelayString = "${quotient.ledger.outbox-poll-ms:1000}")
  public void publishPending() {
    List<OutboxRow> rows =
        jdbc.sql(
                "SELECT id, aggregate_id, payload FROM outbox WHERE published_at IS NULL "
                    + "ORDER BY created_at LIMIT "
                    + BATCH)
            .query(
                (rs, n) ->
                    new OutboxRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("aggregate_id", UUID.class),
                        rs.getString("payload")))
            .list();

    for (OutboxRow row : rows) {
      try {
        kafkaTemplate
            .send(topic, row.aggregateId().toString(), row.payload())
            .get(10, TimeUnit.SECONDS);
        jdbc.sql("UPDATE outbox SET published_at = now() WHERE id = ?").param(row.id()).update();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      } catch (Exception e) {
        // Leave unpublished; a later poll retries. Downstream is idempotent.
        LOG.warn("failed to publish outbox row {}, will retry", row.id(), e);
      }
    }
  }

  private record OutboxRow(UUID id, UUID aggregateId, String payload) {}
}
