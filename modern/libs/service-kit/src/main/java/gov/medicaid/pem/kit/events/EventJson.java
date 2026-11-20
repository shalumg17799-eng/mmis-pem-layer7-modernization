package gov.medicaid.pem.kit.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Reads incoming Kafka messages (JSON envelopes) and their data payloads. */
public final class EventJson {

  private EventJson() {}

  public static EventEnvelope parse(ObjectMapper json, String message) {
    try {
      return json.readValue(message, EventEnvelope.class);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("not a PEM event envelope", e);
    }
  }

  public static <T> T data(ObjectMapper json, EventEnvelope envelope, Class<T> type) {
    try {
      return json.treeToValue(envelope.data(), type);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(
          "event " + envelope.id() + " has invalid data for " + type.getSimpleName(), e);
    }
  }
}
