package io.github.marciomarinho.quotient.ledger.adapter.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.marciomarinho.quotient.common.event.Charge;
import io.github.marciomarinho.quotient.common.event.EventJson;
import io.github.marciomarinho.quotient.ledger.application.LedgerPostingService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes charges from {@code billing.charges.v1} and posts each to the ledger. Posting is
 * idempotent on the deterministic charge id, so a redelivered charge (at-least-once from the rating
 * engine) never double-posts.
 */
@Component
public class ChargeListener {

  private final LedgerPostingService postingService;
  private final ObjectMapper mapper = EventJson.mapper();

  public ChargeListener(LedgerPostingService postingService) {
    this.postingService = postingService;
  }

  @KafkaListener(
      topics = "${quotient.ledger.charges-topic:billing.charges.v1}",
      groupId = "${spring.kafka.consumer.group-id:quotient-ledger-service}")
  public void onCharge(String payload) throws Exception {
    Charge charge = mapper.readValue(payload, Charge.class);
    postingService.post(charge);
  }
}
