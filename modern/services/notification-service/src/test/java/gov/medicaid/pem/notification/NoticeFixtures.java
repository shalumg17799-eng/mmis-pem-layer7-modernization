package gov.medicaid.pem.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.testsupport.PemContainers;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Shared test support of the notification-service Lane 3 tests (MAM-58, MAM-59, MAM-60): events
 * sent to the real Kafka, the real Mailpit read back over its API, the notice tables, and a
 * database trigger that makes the outbox insert of one NPI fail. Every wait has its own deadline,
 * so a defect fails the test and never hangs it. The tests use their own NPIs (10039xxxxx) and
 * never clear the Mailpit, so they do not disturb the Cucumber scenarios.
 */
public final class NoticeFixtures {

  public static final String SANCTIONS = "pem.sanctions.events";
  public static final String PROVIDER = "pem.provider.events";
  public static final String ENROLLMENT = "pem.enrollment.events";
  public static final String REVALIDATION = "pem.revalidation.events";
  public static final String TMSIS = "pem.tmsis.events";

  /** How long a test waits for the service: the retries of a failing event take about 3 s. */
  public static final Duration GUARD = Duration.ofSeconds(60);

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  private NoticeFixtures() {}

  // ---- events
  // ------------------------------------------------------------------------------------

  /** The envelope of an event as the producing service writes it. */
  public static String envelope(
      UUID id, String type, String subject, String correlationId, Map<String, Object> data) {
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("id", id.toString());
    e.put("type", type);
    e.put("source", "lane3-test");
    e.put("time", "2026-11-20T10:00:00Z");
    e.put("subject", subject);
    e.put("correlationId", correlationId);
    e.put("data", data);
    try {
      return JSON.writeValueAsString(e);
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  public static String envelope(UUID id, String type, String subject, Map<String, Object> data) {
    return envelope(id, type, subject, UUID.randomUUID().toString(), data);
  }

  public static void publish(String topic, String key, String value) {
    Map<String, Object> cfg =
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, PemContainers.KAFKA.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    try (var producer = new KafkaProducer<String, String>(cfg)) {
      producer.send(new ProducerRecord<>(topic, key, value)).get(30, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new IllegalStateException("could not publish to " + topic, e);
    }
  }

  /** Polls until the condition holds; fails with the message after the deadline. */
  public static void await(BooleanSupplier condition, Duration limit, String what) {
    long end = System.nanoTime() + limit.toNanos();
    while (System.nanoTime() < end) {
      if (condition.getAsBoolean()) {
        return;
      }
      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError("interrupted while waiting for " + what, e);
      }
    }
    throw new AssertionError(what + " did not happen within " + limit.toSeconds() + " s");
  }

  /** The service has handled the event (its id is in processed_event). */
  public static void awaitProcessed(JdbcTemplate jdbc, UUID eventId) {
    await(() -> processedCount(jdbc, eventId) > 0, GUARD, "event " + eventId + " to be processed");
  }

  public static int processedCount(JdbcTemplate jdbc, UUID eventId) {
    return count(jdbc, "SELECT count(*) FROM processed_event WHERE event_id = ?", eventId);
  }

  /**
   * Publishes a TestMarker with the same topic and key and waits until it is processed: the
   * partition is read in order, so every earlier event of that key, including one that failed and
   * was retried, has been dealt with by then. Returns the marker's event id (for the clean-up).
   */
  public static UUID awaitBehind(JdbcTemplate jdbc, String topic, String key) {
    UUID marker = UUID.randomUUID();
    publish(topic, key, envelope(marker, "TestMarker", key, Map.of()));
    awaitProcessed(jdbc, marker);
    return marker;
  }

  // ---- tables
  // ------------------------------------------------------------------------------------

  public static int count(JdbcTemplate jdbc, String sql, Object... args) {
    Integer n = jdbc.queryForObject(sql, Integer.class, args);
    return n == null ? 0 : n;
  }

  public static int notices(JdbcTemplate jdbc, String npi) {
    return count(jdbc, "SELECT count(*) FROM notice WHERE npi = ?", npi);
  }

  public static int noticeSent(JdbcTemplate jdbc, String npi) {
    return count(
        jdbc, "SELECT count(*) FROM outbox_event WHERE type = 'NoticeSent' AND subject = ?", npi);
  }

  /** The NoticeSent envelopes in the outbox for this NPI, as they will be published. */
  public static List<JsonNode> noticeSentEvents(JdbcTemplate jdbc, String npi) {
    List<JsonNode> out = new ArrayList<>();
    for (String p :
        jdbc.queryForList(
            "SELECT payload::text FROM outbox_event WHERE type = 'NoticeSent' AND subject = ?"
                + " ORDER BY created_at",
            String.class,
            npi)) {
      try {
        out.add(JSON.readTree(p));
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
    return out;
  }

  /** Removes what a test wrote for these NPIs; the rows of other NPIs stay. */
  public static void clean(JdbcTemplate jdbc, List<UUID> eventIds, String... npis) {
    for (String npi : npis) {
      jdbc.update("DELETE FROM notice WHERE npi = ?", npi);
      jdbc.update("DELETE FROM outbox_event WHERE subject = ?", npi);
    }
    for (UUID id : eventIds) {
      jdbc.update("DELETE FROM processed_event WHERE event_id = ?", id);
    }
  }

  // ---- outbox failure
  // ----------------------------------------------------------------------------

  /**
   * From now on the outbox insert of this NPI fails inside the database (a failure after the
   * email).
   */
  public static void failOutboxFor(JdbcTemplate jdbc, String npi) {
    jdbc.execute(
        "CREATE OR REPLACE FUNCTION lane3_fail_outbox() RETURNS trigger AS $$ BEGIN"
            + " IF NEW.subject = '"
            + npi
            + "' THEN RAISE EXCEPTION 'lane3 test: outbox insert refused'; END IF;"
            + " RETURN NEW; END $$ LANGUAGE plpgsql");
    jdbc.execute("DROP TRIGGER IF EXISTS lane3_fail_outbox ON outbox_event");
    jdbc.execute(
        "CREATE TRIGGER lane3_fail_outbox BEFORE INSERT ON outbox_event"
            + " FOR EACH ROW EXECUTE FUNCTION lane3_fail_outbox()");
  }

  /** Puts the outbox back to normal. */
  public static void restoreOutbox(JdbcTemplate jdbc) {
    jdbc.execute("DROP TRIGGER IF EXISTS lane3_fail_outbox ON outbox_event");
    jdbc.execute("DROP FUNCTION IF EXISTS lane3_fail_outbox()");
  }

  // ---- Mailpit
  // -----------------------------------------------------------------------------------

  /** The emails in Mailpit addressed to this address, newest first. */
  public static List<JsonNode> mailTo(String address) {
    try {
      HttpResponse<String> r =
          HTTP.send(
              HttpRequest.newBuilder(URI.create(TestMail.api() + "/messages?limit=500"))
                  .timeout(Duration.ofSeconds(10))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(r.statusCode()).as("Mailpit answer").isEqualTo(200);
      List<JsonNode> out = new ArrayList<>();
      for (JsonNode m : JSON.readTree(r.body()).path("messages")) {
        for (JsonNode to : m.path("To")) {
          if (to.path("Address").asText().equals(address)) {
            out.add(m);
          }
        }
      }
      return out;
    } catch (java.io.IOException e) {
      throw new IllegalStateException("cannot read Mailpit", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while reading Mailpit", e);
    }
  }

  /** The subjects of the emails to the address, sorted. */
  public static List<String> subjectsTo(String address) {
    return mailTo(address).stream().map(m -> m.path("Subject").asText()).sorted().toList();
  }

  /** The subject of a template as seeded. */
  public static String subjectOf(JdbcTemplate jdbc, String template) {
    return jdbc.queryForObject(
        "SELECT subject FROM notice_template WHERE code = ?", String.class, template);
  }

  public static String mailbox(String npi) {
    return npi + "@providers.pem.local";
  }
}
