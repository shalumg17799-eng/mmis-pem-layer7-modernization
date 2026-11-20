package gov.medicaid.pem.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/** Reads and sends PEM event envelopes on a real Kafka (Testcontainers) in integration tests. */
public final class EventProbe {

  private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

  private EventProbe() {}

  /** Waits for the first event on the topic that matches; fails the test after the timeout. */
  public static JsonNode await(
      String bootstrap, String topic, Predicate<JsonNode> match, Duration timeout) {
    Map<String, Object> cfg =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            bootstrap,
            ConsumerConfig.GROUP_ID_CONFIG,
            "probe-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class);
    List<String> seen = new ArrayList<>();
    try (var consumer = new KafkaConsumer<String, String>(cfg)) {
      consumer.subscribe(List.of(topic));
      long end = System.nanoTime() + timeout.toNanos();
      while (System.nanoTime() < end) {
        for (var rec : consumer.poll(Duration.ofMillis(250))) {
          JsonNode n = read(rec.value());
          seen.add(n.path("type").asText() + "/" + n.path("subject").asText());
          if (match.test(n)) {
            return n;
          }
        }
      }
    }
    throw new AssertionError(
        "no matching event on " + topic + " within " + timeout + "; seen " + seen);
  }

  /** Publishes a raw envelope (as another service would) with the NPI as key. */
  public static void send(String bootstrap, String topic, String key, String envelopeJson) {
    Map<String, Object> cfg =
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    try (var producer = new KafkaProducer<String, String>(cfg)) {
      producer.send(new ProducerRecord<>(topic, key, envelopeJson)).get();
    } catch (Exception e) {
      throw new IllegalStateException("could not send test event", e);
    }
  }

  /** Builds an envelope as the producing service would. */
  public static String envelope(String type, String source, String subject, Object data) {
    try {
      return JSON.writeValueAsString(
          Map.of(
              "id", UUID.randomUUID().toString(),
              "type", type,
              "source", source,
              "time", "2026-01-15T10:00:00Z",
              "subject", subject,
              "correlationId", UUID.randomUUID().toString(),
              "data", data));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static JsonNode read(String text) {
    try {
      return JSON.readTree(text);
    } catch (Exception e) {
      return JSON.createObjectNode();
    }
  }
}
