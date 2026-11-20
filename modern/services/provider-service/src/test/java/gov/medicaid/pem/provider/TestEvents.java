package gov.medicaid.pem.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.testsupport.PemContainers;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Events as the other services publish them (the envelope of events.yaml), sent to the real Kafka
 * of the test and awaited through the processed_event table. Every wait carries its own time limit,
 * so a defect makes the test fail and never hang.
 */
public final class TestEvents {

  public static final String ENROLLMENT = "pem.enrollment.events";
  public static final String SCREENING = "pem.screening.events";
  public static final String RISK = "pem.risk.events";
  public static final String NOTIFICATION = "pem.notification.events";

  private static final Duration LIMIT = Duration.ofSeconds(45);

  private TestEvents() {}

  /** The JSON envelope: id, type, source, time, subject (the NPI), correlationId and data. */
  public static String envelope(
      ObjectMapper json,
      UUID id,
      String type,
      String source,
      String npi,
      String correlationId,
      Object data) {
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("id", id.toString());
    e.put("type", type);
    e.put("source", source);
    e.put("time", "2026-11-18T10:00:00Z");
    e.put("subject", npi);
    e.put("correlationId", correlationId);
    e.put("data", data);
    try {
      return json.writeValueAsString(e);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException(ex);
    }
  }

  /** Sends a raw message with the NPI as key and waits for the broker's acknowledgement. */
  public static void send(String topic, String key, String message) {
    Map<String, Object> cfg =
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
            PemContainers.KAFKA.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
            StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
            StringSerializer.class,
            ProducerConfig.MAX_BLOCK_MS_CONFIG,
            30000);
    try (var producer = new KafkaProducer<String, String>(cfg)) {
      producer.send(new ProducerRecord<>(topic, key, message)).get();
    } catch (Exception e) {
      throw new IllegalStateException("could not send the test event to " + topic, e);
    }
  }

  /** Waits until provider-service has recorded the event id as processed; fails after 45 s. */
  public static void awaitProcessed(JdbcTemplate jdbc, UUID id) {
    long end = System.nanoTime() + LIMIT.toNanos();
    while (System.nanoTime() < end) {
      Integer n =
          jdbc.queryForObject(
              "SELECT count(*) FROM processed_event WHERE event_id = ?", Integer.class, id);
      if (n != null && n > 0) {
        return;
      }
      sleep(200);
    }
    throw new AssertionError("event " + id + " was not processed by provider-service within 45 s");
  }

  /** Sends one event and waits for it to be processed. */
  public static void sendAndAwait(
      ObjectMapper json,
      JdbcTemplate jdbc,
      String topic,
      UUID id,
      String type,
      String source,
      String npi,
      String correlationId,
      Object data) {
    send(topic, npi, envelope(json, id, type, source, npi, correlationId, data));
    awaitProcessed(jdbc, id);
  }

  /** ApplicationValidated data of an individual, as enrollment-service publishes it. */
  public static Map<String, Object> individualApplication(
      String npi, String medicaidId, String last, String first) {
    Map<String, Object> d = new LinkedHashMap<>();
    d.put("applicationId", UUID.randomUUID().toString());
    d.put("npi", npi);
    d.put("medicaidId", medicaidId);
    d.put("providerType", "IN");
    d.put("taxonomy", "207Q00000X");
    d.put(
        "individual",
        Map.of("lastName", last, "firstName", first, "licenseNo", "MD12345", "licenseState", "TX"));
    return d;
  }

  /** ScreeningCompleted data with the four sources of PRVSCR01. */
  public static Map<String, Object> screening(String npi, String result) {
    List<Map<String, String>> sources =
        List.of(
            Map.of("source", "LICBOARD", "result", "P", "detail", "X"),
            Map.of("source", "OIGLEIE", "result", "PASS".equals(result) ? "P" : "F", "detail", "X"),
            Map.of("source", "SAMGOV", "result", "P", "detail", "X"),
            Map.of("source", "SSADMF", "result", "P", "detail", "X"));
    return Map.of(
        "screeningId",
        UUID.randomUUID().toString(),
        "npi",
        npi,
        "reason",
        "ENROLLMENT",
        "result",
        result,
        "taxonomy",
        "207Q00000X",
        "sources",
        sources);
  }

  /** RiskTierAssigned data. */
  public static Map<String, Object> riskTier(String npi, String tier) {
    return Map.of("npi", npi, "taxonomy", "207Q00000X", "tier", tier, "ruleSetVersion", 1);
  }

  /** NoticeSent data (events.yaml NoticeSentData). */
  public static Map<String, Object> noticeSent(String npi, UUID noticeId, String template) {
    return Map.of("noticeId", noticeId.toString(), "npi", npi, "template", template);
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("interrupted while waiting for an event", e);
    }
  }
}
