package io.github.marciomarinho.quotient.common.event;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Factory for the one {@link ObjectMapper} configuration used to (de)serialize event payloads
 * across every service.
 *
 * <p>Having a single definition here guarantees the gateway, aggregator, rating engine, and ledger
 * all agree byte-for-byte on the wire format — timestamps as ISO-8601 strings (not numeric arrays),
 * unknown fields ignored so a newer producer field never breaks an older consumer, and no failure
 * on empty beans.
 */
public final class EventJson {

  private EventJson() {}

  /** A new, fully configured mapper. Callers may share a single instance. */
  public static ObjectMapper mapper() {
    return new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }
}
