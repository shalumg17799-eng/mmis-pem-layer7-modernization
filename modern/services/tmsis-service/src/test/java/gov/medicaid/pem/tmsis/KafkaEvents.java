package gov.medicaid.pem.tmsis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.testsupport.PemContainers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Reads the events tmsis-service published on pem.tmsis.events from the real Kafka of the test run,
 * with their message keys (the shared EventProbe returns the value only). It never waits longer
 * than the time it is given.
 */
public final class KafkaEvents {

  public static final String TOPIC = "pem.tmsis.events";

  private static final ObjectMapper JSON = new ObjectMapper();

  /** One Kafka record: the message key and the event envelope. */
  public record Message(String key, JsonNode event) {}

  private KafkaEvents() {}

  /**
   * Reads the topic from its beginning for {@code window} and returns the ExtractProduced events of
   * this run (data.runId), or all events if {@code runId} is null. Returns as soon as one is found
   * and {@code settle} has passed since then, so that a duplicate would be seen too.
   */
  public static List<Message> extractProduced(String runId, Duration window, Duration settle) {
    Map<String, Object> cfg =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            PemContainers.KAFKA.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG,
            "tmsis-test-probe-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class);
    List<Message> found = new ArrayList<>();
    long end = System.nanoTime() + window.toNanos();
    long settleUntil = Long.MAX_VALUE;
    try (var consumer = new KafkaConsumer<String, String>(cfg)) {
      consumer.subscribe(List.of(TOPIC));
      while (System.nanoTime() < Math.min(end, settleUntil)) {
        for (var rec : consumer.poll(Duration.ofMillis(250))) {
          JsonNode event = read(rec.value());
          boolean mine =
              "ExtractProduced".equals(event.path("type").asText())
                  && (runId == null || runId.equals(event.path("data").path("runId").asText()));
          if (mine) {
            found.add(new Message(rec.key(), event));
            if (settleUntil == Long.MAX_VALUE) {
              settleUntil = System.nanoTime() + settle.toNanos();
            }
          }
        }
      }
    }
    return found;
  }

  private static JsonNode read(String text) {
    try {
      return JSON.readTree(text);
    } catch (Exception e) {
      return JSON.createObjectNode();
    }
  }
}
