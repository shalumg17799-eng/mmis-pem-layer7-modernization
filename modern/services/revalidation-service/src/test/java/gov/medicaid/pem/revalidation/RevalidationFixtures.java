package gov.medicaid.pem.revalidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.testsupport.PemJwt;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Fixtures of the revalidation-service integration tests (real Postgres and Kafka): the four tables
 * the stories touch, runs of the sweep through the API, event envelopes as the other services send
 * them, and failures of the real database made with triggers that reject one kind of write.
 *
 * <p>Nothing here can wait for ever: a run is awaited for {@link #BOUND} at most, so a sweep that
 * never ends fails the test instead of hanging the build.
 */
public final class RevalidationFixtures {

  public static final ObjectMapper JSON = new ObjectMapper();
  public static final String SWEEPS = "/api/v1/batch/revalidation-sweeps";
  public static final Duration BOUND = Duration.ofSeconds(45);
  public static final String SCREENING_TOPIC = "pem.screening.events";
  public static final String PROVIDER_TOPIC = "pem.provider.events";

  private RevalidationFixtures() {}

  // ---- the database ---------------------------------------------------------------------------

  /** An empty service: no failure injected, no job running, no case, run, provider or event. */
  public static void reset(JdbcTemplate jdbc) {
    dropFailures(jdbc);
    awaitNoRunningJob(jdbc);
    jdbc.update("DELETE FROM revalidation_case");
    jdbc.update("DELETE FROM sweep_run");
    jdbc.update("DELETE FROM tracked_provider");
    jdbc.update("DELETE FROM outbox_event");
  }

  public static void awaitNoRunningJob(JdbcTemplate jdbc) {
    Awaitility.await("no sweep job is still running")
        .atMost(BOUND)
        .pollInterval(Duration.ofMillis(200))
        .until(
            () ->
                jdbc.queryForObject(
                        "SELECT count(*) FROM batch_job_execution"
                            + " WHERE status IN ('STARTING', 'STARTED')",
                        Integer.class)
                    == 0);
  }

  /** The service tracks the provider (the read model that ProviderStatusChanged feeds). */
  public static void track(JdbcTemplate jdbc, String npi, String status, LocalDate due) {
    jdbc.update(
        "INSERT INTO tracked_provider (npi, enroll_status, reval_due_date) VALUES (?, ?, ?)"
            + " ON CONFLICT (npi) DO UPDATE SET enroll_status = EXCLUDED.enroll_status,"
            + " reval_due_date = EXCLUDED.reval_due_date",
        npi,
        status,
        due == null ? null : Date.valueOf(due));
  }

  /** A case in the given status; a closed case has a closedAt. Returns its public id. */
  public static UUID insertCase(JdbcTemplate jdbc, String npi, LocalDate due, String status) {
    UUID id = UUID.randomUUID();
    boolean closed = status.equals("COMPLETED") || status.equals("CANCELLED");
    jdbc.update(
        "INSERT INTO revalidation_case (public_id, npi, due_date, status, opened_at, closed_at)"
            + " VALUES (?, ?, ?, ?, ?, ?)",
        id,
        npi,
        Date.valueOf(due),
        status,
        Timestamp.from(Instant.parse("2026-03-01T10:00:00Z")),
        closed ? Timestamp.from(Instant.parse("2026-03-15T10:00:00Z")) : null);
    return id;
  }

  /** A sweep run row as the service would leave it. */
  public static UUID insertRun(
      JdbcTemplate jdbc,
      String status,
      LocalDate businessDate,
      int queued,
      int noDueDate,
      String queueFile,
      String failureReason) {
    UUID id = UUID.randomUUID();
    boolean ended = !status.equals("RUNNING");
    jdbc.update(
        "INSERT INTO sweep_run (public_id, business_date, status, queued_count, no_due_date_count,"
            + " queue_file, failure_reason, started_at, ended_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        id,
        Date.valueOf(businessDate),
        status,
        queued,
        noDueDate,
        queueFile,
        failureReason,
        Timestamp.from(Instant.parse("2026-03-31T08:00:00Z")),
        ended ? Timestamp.from(Instant.parse("2026-03-31T08:00:05Z")) : null);
    return id;
  }

  public static int count(JdbcTemplate jdbc, String sql, Object... args) {
    Integer n = jdbc.queryForObject(sql, Integer.class, args);
    return n == null ? 0 : n;
  }

  public static int casesOf(JdbcTemplate jdbc, String npi) {
    return count(jdbc, "SELECT count(*) FROM revalidation_case WHERE npi = ?", npi);
  }

  public static String caseStatus(JdbcTemplate jdbc, UUID caseId) {
    return jdbc.queryForObject(
        "SELECT status FROM revalidation_case WHERE public_id = ?", String.class, caseId);
  }

  /** The envelopes of this type in the outbox, oldest first. */
  public static List<JsonNode> outbox(JdbcTemplate jdbc, String type) {
    List<JsonNode> events = new ArrayList<>();
    for (String payload :
        jdbc.queryForList(
            "SELECT payload::text FROM outbox_event WHERE type = ? ORDER BY created_at, id",
            String.class,
            type)) {
      events.add(read(payload));
    }
    return events;
  }

  public static JsonNode read(String json) {
    try {
      return JSON.readTree(json);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("not JSON: " + json, e);
    }
  }

  // ---- failures of the real database ----------------------------------------------------------

  /**
   * Makes every write of this kind to the table fail with the message, until {@link #dropFailures}.
   * {@code operation} is INSERT or UPDATE; {@code condition} is a boolean SQL expression on {@code
   * NEW} ("true" rejects every write).
   */
  public static void reject(
      JdbcTemplate jdbc,
      String name,
      String operation,
      String table,
      String condition,
      String message) {
    jdbc.execute(
        "CREATE OR REPLACE FUNCTION rev_reject_"
            + name
            + "() RETURNS trigger AS $$ BEGIN IF "
            + condition
            + " THEN RAISE EXCEPTION '"
            + message
            + "'; END IF; RETURN NEW; END; $$ LANGUAGE plpgsql");
    jdbc.execute(
        "CREATE TRIGGER rev_reject_"
            + name
            + " BEFORE "
            + operation
            + " ON "
            + table
            + " FOR EACH ROW EXECUTE FUNCTION rev_reject_"
            + name
            + "()");
  }

  /** Lifts every failure made by {@link #reject} (and the one of the Cucumber step). */
  public static void dropFailures(JdbcTemplate jdbc) {
    List<Map<String, Object>> triggers =
        jdbc.queryForList(
            "SELECT t.tgname AS name, c.relname AS tbl FROM pg_trigger t"
                + " JOIN pg_class c ON c.oid = t.tgrelid"
                + " WHERE NOT t.tgisinternal AND (t.tgname LIKE 'rev\\_reject\\_%'"
                + " OR t.tgname = 'revalidation_test_fail')");
    for (Map<String, Object> t : triggers) {
      jdbc.execute("DROP TRIGGER IF EXISTS " + t.get("name") + " ON " + t.get("tbl"));
      jdbc.execute("DROP FUNCTION IF EXISTS " + t.get("name") + "()");
    }
  }

  // ---- the sweep through the API --------------------------------------------------------------

  /** POST the sweep with a raw body as the given user (null: no token). */
  public static MvcResult startSweep(MockMvc mvc, RequestPostProcessor token, String body)
      throws Exception {
    var request = post(SWEEPS).contentType(MediaType.APPLICATION_JSON).content(body);
    return mvc.perform(token == null ? request : request.with(token)).andReturn();
  }

  /** Starts a sweep as ADMIN, asserts 202 and returns the run id (the run may still be RUNNING). */
  public static UUID startAsAdmin(MockMvc mvc, String businessDate) throws Exception {
    MvcResult r = startSweep(mvc, PemJwt.admin(), "{\"businessDate\":\"" + businessDate + "\"}");
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(202);
    return UUID.fromString(read(r.getResponse().getContentAsString()).path("runId").asText());
  }

  /** Waits until the run is no longer RUNNING and returns what GET shows (BOUND at most). */
  public static JsonNode awaitRun(MockMvc mvc, UUID run) {
    JsonNode[] last = new JsonNode[1];
    Awaitility.await("sweep run " + run + " ends (COMPLETED or FAILED)")
        .atMost(BOUND)
        .pollInterval(Duration.ofMillis(200))
        .until(
            () -> {
              MvcResult r =
                  mvc.perform(get(SWEEPS + "/{runId}", run).with(PemJwt.admin())).andReturn();
              last[0] = read(r.getResponse().getContentAsString());
              return r.getResponse().getStatus() == 200
                  && !"RUNNING".equals(last[0].path("status").asText());
            });
    return last[0];
  }

  /** A sweep for the date, awaited; asserts that it ended COMPLETED. */
  public static UUID sweep(MockMvc mvc, String businessDate) throws Exception {
    UUID run = startAsAdmin(mvc, businessDate);
    JsonNode ended = awaitRun(mvc, run);
    assertThat(ended.path("status").asText()).as(ended.toString()).isEqualTo("COMPLETED");
    return run;
  }

  // ---- events as the other services send them -------------------------------------------------

  public static String envelope(
      UUID id,
      String type,
      String source,
      String subject,
      Map<String, Object> data,
      String correlationId) {
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("id", id.toString());
    e.put("type", type);
    e.put("source", source);
    e.put("time", "2026-11-19T10:00:00Z");
    e.put("subject", subject);
    e.put("correlationId", correlationId);
    e.put("data", data);
    try {
      return JSON.writeValueAsString(e);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException(ex);
    }
  }

  public static String providerStatusChanged(
      UUID id, String npi, String from, String to, String revalidationDueDate) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("npi", npi);
    data.put("from", from);
    data.put("to", to);
    data.put("revalidationDueDate", revalidationDueDate);
    return envelope(
        id, "ProviderStatusChanged", "provider-service", npi, data, UUID.randomUUID().toString());
  }

  public static String screeningCompleted(
      UUID id, String npi, String reason, String result, UUID screeningId) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("screeningId", screeningId.toString());
    data.put("npi", npi);
    data.put("reason", reason);
    data.put("result", result);
    return envelope(
        id, "ScreeningCompleted", "screening-service", npi, data, UUID.randomUUID().toString());
  }

  public static String revalidationDue(UUID id, UUID caseId, String npi, String dueDate) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("caseId", caseId.toString());
    data.put("npi", npi);
    data.put("dueDate", dueDate);
    return envelope(
        id, "RevalidationDue", "revalidation-service", npi, data, UUID.randomUUID().toString());
  }
}
