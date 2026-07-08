package io.github.marciomarinho.quotient.rating.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.marciomarinho.quotient.common.event.EventJson;
import io.github.marciomarinho.quotient.common.event.MeterReading;
import io.github.marciomarinho.quotient.rating.application.RatingService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes meter readings from {@code usage.aggregates.v1}, rates each one, and publishes the
 * resulting charge. Readings that are not priced are simply acknowledged with no charge emitted.
 */
@Component
public class MeterReadingListener {

  private final RatingService ratingService;
  private final ChargePublisher chargePublisher;
  private final ObjectMapper mapper = EventJson.mapper();

  public MeterReadingListener(RatingService ratingService, ChargePublisher chargePublisher) {
    this.ratingService = ratingService;
    this.chargePublisher = chargePublisher;
  }

  @KafkaListener(
      topics = "${quotient.rating.input-topic:usage.aggregates.v1}",
      groupId = "${spring.kafka.consumer.group-id:quotient-rating-engine}")
  public void onReading(String payload) throws Exception {
    MeterReading reading = mapper.readValue(payload, MeterReading.class);
    ratingService.rate(reading).ifPresent(chargePublisher::publish);
  }
}
