package io.github.marciomarinho.quotient.aggregator.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.marciomarinho.quotient.common.event.EventJson;
import java.io.UncheckedIOException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

/**
 * A Kafka {@link Serde} that (de)serializes a type as JSON using the shared {@link EventJson}
 * mapper, so the aggregator reads/writes exactly the same wire format the gateways and rating
 * engine use. Records are handled natively by Jackson.
 */
public final class JsonSerde<T> implements Serde<T> {

  private final ObjectMapper mapper = EventJson.mapper();
  private final Class<T> type;

  public JsonSerde(Class<T> type) {
    this.type = type;
  }

  @Override
  public Serializer<T> serializer() {
    return (topic, data) -> {
      if (data == null) {
        return null;
      }
      try {
        return mapper.writeValueAsBytes(data);
      } catch (Exception e) {
        throw new UncheckedIOException("failed to serialize " + type.getSimpleName(), asIo(e));
      }
    };
  }

  @Override
  public Deserializer<T> deserializer() {
    return (topic, bytes) -> {
      if (bytes == null) {
        return null;
      }
      try {
        return mapper.readValue(bytes, type);
      } catch (Exception e) {
        throw new UncheckedIOException("failed to deserialize " + type.getSimpleName(), asIo(e));
      }
    };
  }

  private static java.io.IOException asIo(Exception e) {
    return e instanceof java.io.IOException io ? io : new java.io.IOException(e);
  }
}
