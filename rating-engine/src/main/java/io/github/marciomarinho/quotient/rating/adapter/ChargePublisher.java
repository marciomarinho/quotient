package io.github.marciomarinho.quotient.rating.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.marciomarinho.quotient.common.event.Charge;
import io.github.marciomarinho.quotient.common.event.EventJson;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes {@link Charge}s to {@code billing.charges.v1}, keyed by tenant. The send is awaited so
 * the charge is durably written before the source reading's offset is committed (at-least-once);
 * downstream, the ledger's idempotent posting makes a replay harmless.
 */
@Component
public class ChargePublisher {

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper mapper = EventJson.mapper();
  private final String topic;

  public ChargePublisher(
      KafkaTemplate<String, String> kafkaTemplate,
      @Value("${quotient.rating.output-topic:billing.charges.v1}") String topic) {
    this.kafkaTemplate = kafkaTemplate;
    this.topic = topic;
  }

  /** Publish a charge and wait for the broker ack. */
  public void publish(Charge charge) {
    try {
      String payload = mapper.writeValueAsString(charge);
      kafkaTemplate.send(topic, charge.tenantId().asString(), payload).get(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted publishing charge " + charge.chargeId(), e);
    } catch (Exception e) {
      throw new IllegalStateException("failed to publish charge " + charge.chargeId(), e);
    }
  }
}
