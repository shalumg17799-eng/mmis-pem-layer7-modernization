package gov.medicaid.pem.screening;

import static gov.medicaid.pem.screening.TestSources.MOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.testsupport.PemJwt;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Base of the integration tests of Lane 3 (MAM-20, MAM-22, MAM-28, MAM-32): the real application
 * with Postgres, Kafka and the WireMock of TestSources, a clean mocked world before every test and
 * the helpers to send events, start runs and wait for them. Nothing here can wait for ever: every
 * wait has a bound and fails the test with a message when it is reached.
 */
public abstract class Lane3Test extends IntegrationTest {

  /** The longest a test waits for something that runs in the background. */
  protected static final Duration BOUND = Bounded.BOUND;

  @Autowired protected MockMvc mvc;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected ObjectMapper json;

  private final List<String> rejections = new ArrayList<>();

  @BeforeEach
  protected void freshWorld() {
    MOCK.resetAll();
    TestSources.stubToken();
    ScreeningWorld.cleanSources();
    dropLeakedRejections();
    cleanTables();
  }

  /**
   * Safety net: a rejection that an earlier test did not lift (for example a test that was cut off)
   * never reaches the next test.
   */
  private void dropLeakedRejections() {
    List<String> leaked =
        jdbc.queryForList(
            "SELECT t.tgname || '|' || c.relname FROM pg_trigger t JOIN pg_class c ON c.oid = t.tgrelid"
                + " WHERE NOT t.tgisinternal AND t.tgname LIKE 'reject\\_%'",
            String.class);
    for (String entry : leaked) {
      String name = entry.substring(0, entry.indexOf('|'));
      jdbc.execute("DROP TRIGGER IF EXISTS " + name + " ON " + entry.substring(name.length() + 1));
      jdbc.execute("DROP FUNCTION IF EXISTS " + name + "()");
    }
  }

  /**
   * Lifts every write rejection the test made, waits until no run is going on any more (a run must
   * not write into the next test's world) and restores the clean stubs.
   */
  @AfterEach
  protected void liftRejections() {
    for (String entry : List.copyOf(rejections)) {
      lift(entry.substring(0, entry.indexOf('|')));
    }
    until(
        "no screening run is RUNNING any more",
        () ->
            jdbc.queryForObject(
                    "SELECT count(*) FROM screening_run WHERE status = 'RUNNING'", Integer.class)
                == 0,
        BOUND);
    MOCK.resetAll();
    TestSources.stubToken();
  }

  protected void cleanTables() {
    jdbc.update("DELETE FROM screening_result");
    jdbc.update("DELETE FROM screening");
    jdbc.update("DELETE FROM screening_run");
    jdbc.update("DELETE FROM outbox_event");
  }

  // ---- database failures ------------------------------------------------------------------

  /**
   * Makes the database reject every write of {@code events} (for example "INSERT" or "INSERT OR
   * UPDATE") on {@code table} for which the SQL condition {@code when} holds. The message of the
   * rejection says nothing about SQL.
   */
  protected void reject(String name, String events, String table, String when) {
    jdbc.execute(
        "CREATE FUNCTION "
            + name
            + "() RETURNS trigger AS $$ BEGIN"
            + " RAISE EXCEPTION 'the test database rejected this write'; END; $$"
            + " LANGUAGE plpgsql");
    jdbc.execute(
        "CREATE TRIGGER "
            + name
            + " BEFORE "
            + events
            + " ON "
            + table
            + " FOR EACH ROW WHEN ("
            + when
            + ") EXECUTE FUNCTION "
            + name
            + "()");
    rejections.add(name + "|" + table);
  }

  /** Lets the writes through again. */
  protected void lift(String name) {
    for (String r : List.copyOf(rejections)) {
      if (r.startsWith(name + "|")) {
        jdbc.execute("DROP TRIGGER IF EXISTS " + name + " ON " + r.substring(name.length() + 1));
        jdbc.execute("DROP FUNCTION IF EXISTS " + name + "()");
        rejections.remove(r);
      }
    }
  }

  // ---- events -----------------------------------------------------------------------------

  protected UUID providerRegistered(String npi) {
    return ScreeningEvents.providerRegistered(npi);
  }

  protected UUID providerRegistered(UUID id, String npi, String correlationId) {
    return ScreeningEvents.providerRegistered(id, npi, correlationId);
  }

  protected UUID revalidationDue(String npi, String dueDate) {
    return ScreeningEvents.revalidationDue(npi, dueDate);
  }

  protected UUID revalidationDue(UUID id, String npi, String dueDate, String correlationId) {
    return ScreeningEvents.revalidationDue(id, npi, dueDate, correlationId);
  }

  /** Sends any text on a topic with the key. */
  protected void send(String topic, String key, String text) {
    ScreeningEvents.send(topic, key, text);
  }

  /** Waits until screening-service has committed the event (its processed_event row exists). */
  protected void awaitProcessed(UUID id) {
    until(
        "event " + id + " is processed by screening-service",
        () ->
            jdbc.queryForObject(
                    "SELECT count(*) FROM processed_event WHERE event_id = ?", Integer.class, id)
                > 0,
        BOUND);
  }

  /** Waits until the NPI has this many COMPLETED screenings. */
  protected void awaitCompleted(String npi, int count) {
    until(
        count + " COMPLETED screening(s) of " + npi,
        () ->
            jdbc.queryForObject(
                    "SELECT count(*) FROM screening WHERE npi = ? AND status = 'COMPLETED'",
                    Integer.class,
                    npi)
                >= count,
        BOUND);
  }

  /** Waits for a condition; when the bound is reached the test fails with the message. */
  protected static void until(String what, Callable<Boolean> condition, Duration bound) {
    Bounded.until(what, condition, bound);
  }

  /**
   * Waits for a message on the topic whose raw text satisfies the condition (for example a message
   * parked on a .DLT topic) and returns it. A message that does not come within the bound fails the
   * test.
   */
  protected String awaitRaw(String topic, java.util.function.Predicate<String> match) {
    java.util.Map<String, Object> cfg =
        java.util.Map.of(
            org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            gov.medicaid.pem.testsupport.PemContainers.KAFKA.getBootstrapServers(),
            org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG,
            "probe-" + UUID.randomUUID(),
            org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest",
            org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            org.apache.kafka.common.serialization.StringDeserializer.class,
            org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            org.apache.kafka.common.serialization.StringDeserializer.class,
            org.apache.kafka.clients.consumer.ConsumerConfig.METADATA_MAX_AGE_CONFIG,
            1000);
    List<String> seen = new ArrayList<>();
    try (var consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<String, String>(cfg)) {
      consumer.subscribe(List.of(topic));
      long end = System.nanoTime() + BOUND.toNanos();
      while (System.nanoTime() < end) {
        for (var rec : consumer.poll(Duration.ofMillis(250))) {
          seen.add(rec.value());
          if (match.test(rec.value())) {
            return rec.value();
          }
        }
      }
    }
    fail("no matching message on %s within %s; seen %s", topic, BOUND, seen);
    return null;
  }

  /** True when no matching message is on the topic within the (short) time. */
  protected boolean noRaw(
      String topic, java.util.function.Predicate<String> match, Duration howLong) {
    java.util.Map<String, Object> cfg =
        java.util.Map.of(
            org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            gov.medicaid.pem.testsupport.PemContainers.KAFKA.getBootstrapServers(),
            org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG,
            "probe-" + UUID.randomUUID(),
            org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest",
            org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            org.apache.kafka.common.serialization.StringDeserializer.class,
            org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            org.apache.kafka.common.serialization.StringDeserializer.class,
            org.apache.kafka.clients.consumer.ConsumerConfig.METADATA_MAX_AGE_CONFIG,
            1000);
    try (var consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<String, String>(cfg)) {
      consumer.subscribe(List.of(topic));
      long end = System.nanoTime() + howLong.toNanos();
      while (System.nanoTime() < end) {
        for (var rec : consumer.poll(Duration.ofMillis(250))) {
          if (match.test(rec.value())) {
            return false;
          }
        }
      }
    }
    return true;
  }

  // ---- REST -------------------------------------------------------------------------------

  /** The token of a role, or null for a request without a token ("NONE"). */
  protected static RequestPostProcessor token(String role) {
    return switch (role) {
      case "ANALYST" -> PemJwt.analyst();
      case "SUPERVISOR" -> PemJwt.supervisor();
      case "ADMIN" -> PemJwt.admin();
      case "SYSTEM" -> PemJwt.system();
      case "PROVIDER" -> PemJwt.provider("1003000111");
      case "NONE" -> null;
      default -> throw new IllegalArgumentException(role);
    };
  }

  protected MvcResult post(String path, RequestPostProcessor who, String body) throws Exception {
    MockHttpServletRequestBuilder req =
        MockMvcRequestBuilders.post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    return mvc.perform(who == null ? req : req.with(who)).andReturn();
  }

  protected MvcResult get(String path, RequestPostProcessor who) throws Exception {
    MockHttpServletRequestBuilder req = MockMvcRequestBuilders.get(path);
    return mvc.perform(who == null ? req : req.with(who)).andReturn();
  }

  protected JsonNode body(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString());
  }

  /** The answer is this status with an application/problem+json body. */
  protected void assertProblem(MvcResult r, int status) throws Exception {
    assertThat(r.getResponse().getStatus())
        .as(r.getResponse().getContentAsString())
        .isEqualTo(status);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
  }

  /** The number of screenings stored for the NPI, any status. */
  protected int screeningCount(String npi) {
    return jdbc.queryForObject("SELECT count(*) FROM screening WHERE npi = ?", Integer.class, npi);
  }

  /** The number of ScreeningCompleted events written to the outbox for the NPI. */
  protected int completedEvents(String npi) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM outbox_event WHERE type = 'ScreeningCompleted' AND subject = ?",
        Integer.class,
        npi);
  }

  /** The envelope of the one ScreeningCompleted event of the NPI, as stored for the relay. */
  protected JsonNode completedEvent(String npi) throws Exception {
    List<String> payloads =
        jdbc.queryForList(
            "SELECT payload::text FROM outbox_event WHERE type = 'ScreeningCompleted' AND subject = ?"
                + " ORDER BY created_at",
            String.class,
            npi);
    assertThat(payloads).as("ScreeningCompleted events of " + npi).hasSize(1);
    return json.readTree(payloads.get(0));
  }

  /** The screening of the NPI with this reason, read through the API as an ANALYST. */
  protected JsonNode screeningOf(String npi) throws Exception {
    MvcResult r = get("/api/v1/screenings?npi=" + npi, PemJwt.analyst());
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    JsonNode items = body(r).path("items");
    assertThat(items).as("screenings of " + npi).hasSize(1);
    return items.get(0);
  }

  /** The result row of one source in a screening read through the API. */
  protected static JsonNode resultOf(JsonNode screening, String source) {
    for (JsonNode r : screening.path("results")) {
      if (source.equals(r.path("source").asText())) {
        return r;
      }
    }
    throw new AssertionError("no " + source + " result in " + screening);
  }

  /**
   * A MANUAL screening of the NPI through the API (202), waited for until it is COMPLETED, read
   * back by its id as an ANALYST.
   */
  protected JsonNode screen(String npi) throws Exception {
    MvcResult r = post("/api/v1/screenings", token("ANALYST"), "{\"npi\":\"" + npi + "\"}");
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(202);
    String id = body(r).path("screeningId").asText();
    JsonNode[] done = new JsonNode[1];
    until(
        "the MANUAL screening " + id + " of " + npi + " is COMPLETED",
        () -> {
          MvcResult g = get("/api/v1/screenings/" + id, PemJwt.analyst());
          if (g.getResponse().getStatus() != 200) {
            return false;
          }
          done[0] = body(g);
          return "COMPLETED".equals(done[0].path("status").asText());
        },
        BOUND);
    return done[0];
  }

  // ---- batch runs -------------------------------------------------------------------------

  protected static final String RUNS = "/api/v1/batch/screening-runs";

  /** Starts a run as the role and returns the 202 answer. */
  protected JsonNode startRun(String role, String businessDate) throws Exception {
    MvcResult r = post(RUNS, token(role), "{\"businessDate\":\"" + businessDate + "\"}");
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(202);
    return body(r);
  }

  /** The run as an ADMIN reads it. */
  protected JsonNode run(String runId) throws Exception {
    MvcResult r = get(RUNS + "/" + runId, PemJwt.admin());
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
    return body(r);
  }

  /** Waits for the run to leave RUNNING and returns it; a run still RUNNING fails the test. */
  protected JsonNode awaitRun(String runId) throws Exception {
    try {
      Awaitility.await("screening run " + runId + " has ended")
          .atMost(BOUND)
          .pollInterval(Duration.ofMillis(100))
          .until(() -> !"RUNNING".equals(run(runId).path("status").asText()));
    } catch (ConditionTimeoutException e) {
      fail("run %s is still RUNNING after %s: %s", runId, BOUND, run(runId));
    }
    return run(runId);
  }

  /** Starts a run as ADMIN for the date and waits for its end. */
  protected JsonNode runToEnd(String businessDate) throws Exception {
    return awaitRun(startRun("ADMIN", businessDate).path("runId").asText());
  }

  /** The SCRNOUT file of a COMPLETED run, downloaded as ADMIN. */
  protected byte[] resultsFile(String runId) throws Exception {
    MvcResult r = get(RUNS + "/" + runId + "/results-file", PemJwt.admin());
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    return r.getResponse().getContentAsByteArray();
  }
}
