package gov.medicaid.pem.revalidation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.revalidation.IntegrationTest;
import gov.medicaid.pem.revalidation.RevalidationFixtures;
import gov.medicaid.pem.testsupport.PemJwt;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * A sweep that fails on a database error (MAM-45 AC-8, D-024), and the work that must not be left
 * half done (lesson c of the earlier reviews):
 *
 * <ul>
 *   <li>the run is FAILED, never COMPLETED, and its reason (the BatchRun error and the stored
 *       failure_reason) is short, at most 200 characters, and holds no SQL text: none of "select",
 *       "insert", "update" or "sql" in any case (lesson a);
 *   <li>the full exception is logged, with the run id (lesson a);
 *   <li>the cases, the RevalidationDue events, the counts and the REVALQ file are written in one
 *       transaction: when the write fails in between, nothing of the run stays, so a re-run applies
 *       the work once, not twice and not never (lesson c).
 * </ul>
 *
 * The failures of the real database are made with triggers that reject one kind of write, lifted
 * after every test. Nothing waits for ever: a run is awaited for 45 seconds at most.
 */
@Timeout(240)
class SweepFailureTest extends IntegrationTest {

  static final String NPI = "1003000222";
  static final String[] SQL_WORDS = {"select", "insert", "update", "sql"};
  static final int SHORT = 200;

  /** The database operation that fails during a sweep. */
  enum Failure {
    /** the insert of the case */
    CASE_INSERT("revalidation_case", "INSERT", "true", "SYNTHETIC DISK FULL"),
    /** the insert of the RevalidationDue event, after the case was inserted */
    EVENT_INSERT("outbox_event", "INSERT", "true", "SYNTHETIC OUTBOX DOWN"),
    /** the write that completes the run with its counts and its file */
    RUN_COMPLETE("sweep_run", "UPDATE", "NEW.status = 'COMPLETED'", "SYNTHETIC RUN TABLE LOCKED"),
    /** the job repository when the job is started */
    JOB_LAUNCH("batch_job_instance", "INSERT", "true", "SYNTHETIC JOB REPOSITORY DOWN");

    final String table;
    final String operation;
    final String condition;
    final String message;

    Failure(String table, String operation, String condition, String message) {
      this.table = table;
      this.operation = operation;
      this.condition = condition;
      this.message = message;
    }
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
  private final Capture capture = new Capture();

  /** What a log line was when it was written (the MDC is thread-local, so it is copied). */
  record Logged(String level, String message, String throwable) {}

  static final class Capture extends AppenderBase<ILoggingEvent> {
    final List<Logged> lines = new CopyOnWriteArrayList<>();

    @Override
    protected void append(ILoggingEvent event) {
      lines.add(
          new Logged(
              event.getLevel().toString(),
              event.getFormattedMessage(),
              event.getThrowableProxy() == null
                  ? ""
                  : ThrowableProxyUtil.asString(event.getThrowableProxy())));
    }
  }

  @BeforeEach
  void startCapture() {
    RevalidationFixtures.reset(jdbc);
    RevalidationFixtures.track(jdbc, NPI, "A", LocalDate.of(2026, 2, 28));
    capture.setContext(root.getLoggerContext());
    capture.start();
    root.addAppender(capture);
  }

  @AfterEach
  void cleanUp() {
    root.detachAppender(capture);
    capture.stop();
    RevalidationFixtures.reset(jdbc);
  }

  private void inject(Failure failure) {
    RevalidationFixtures.reject(
        jdbc,
        failure.name().toLowerCase(),
        failure.operation,
        failure.table,
        failure.condition,
        failure.message);
  }

  /** Makes the failure, starts a sweep as ADMIN and returns the run read back once it has ended. */
  private JsonNode failedRun(Failure failure) throws Exception {
    inject(failure);
    UUID run = RevalidationFixtures.startAsAdmin(mvc, "2026-03-31");
    JsonNode ended = RevalidationFixtures.awaitRun(mvc, run);
    assertThat(ended.path("status").asText())
        .as("%s must end the run FAILED, run: %s", failure, ended)
        .isEqualTo("FAILED");
    return ended;
  }

  private String stored(JsonNode run) {
    return jdbc.queryForObject(
        "SELECT failure_reason FROM sweep_run WHERE public_id = ?::uuid",
        String.class,
        run.path("runId").asText());
  }

  // ---- lesson a: a short reason without SQL text
  // -------------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-45 AC-8] a database error at {0}: the run is FAILED and its error has no SQL statement text")
  @EnumSource(Failure.class)
  void errorHasNoSqlText(Failure failure) throws Exception {
    JsonNode run = failedRun(failure);

    assertThat(run.path("error").asText().toLowerCase()).doesNotContain(SQL_WORDS);
  }

  @ParameterizedTest(
      name =
          "[MAM-45 AC-8] a database error at {0}: the error of the run is a short reason, not blank and at most 200 characters")
  @EnumSource(Failure.class)
  void errorIsShort(Failure failure) throws Exception {
    JsonNode run = failedRun(failure);

    assertThat(run.path("error").asText()).isNotBlank().hasSizeLessThanOrEqualTo(SHORT);
  }

  @ParameterizedTest(
      name =
          "[MAM-45 AC-8] a database error at {0}: the failure_reason stored with the run has no SQL text and is at most 200 characters")
  @EnumSource(Failure.class)
  void storedReasonIsShortAndHasNoSqlText(Failure failure) throws Exception {
    JsonNode run = failedRun(failure);

    String stored = stored(run);
    assertThat(stored).isNotBlank().hasSizeLessThanOrEqualTo(SHORT);
    assertThat(stored.toLowerCase()).doesNotContain(SQL_WORDS);
  }

  @ParameterizedTest(
      name =
          "[MAM-45 AC-8] a database error at {0}: the short reason still says what the database reported")
  @EnumSource(Failure.class)
  void reasonKeepsTheDatabaseMessage(Failure failure) throws Exception {
    JsonNode run = failedRun(failure);

    assertThat(run.path("error").asText()).contains(failure.message);
  }

  @ParameterizedTest(
      name =
          "[MAM-45 AC-8] a database error at {0}: the failed run has an end time and no REVALQ file (404)")
  @EnumSource(Failure.class)
  void failedRunHasNoFile(Failure failure) throws Exception {
    JsonNode run = failedRun(failure);

    assertThat(run.path("endedAt").asText()).isNotBlank();
    assertThat(
            mvc.perform(
                    MockMvcRequestBuilders.get(
                            RevalidationFixtures.SWEEPS + "/{runId}/queue-file",
                            run.path("runId").asText())
                        .with(PemJwt.admin()))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(404);
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-8] the full exception is logged at ERROR with the run id, while the run shows only the short reason")
  void fullExceptionIsLogged() throws Exception {
    JsonNode run = failedRun(Failure.CASE_INSERT);

    String runId = run.path("runId").asText();
    assertThat(capture.lines)
        .as("an ERROR line names the failed run")
        .anySatisfy(
            l -> {
              assertThat(l.level()).isEqualTo(Level.ERROR.toString());
              assertThat(l.message()).contains(runId).contains("FAILED");
            });
    assertThat(capture.lines)
        .as(
            "an ERROR event carries the whole exception, with the statement and the database message")
        .anySatisfy(
            l -> {
              assertThat(l.level()).isEqualTo(Level.ERROR.toString());
              assertThat(l.throwable())
                  .contains("SYNTHETIC DISK FULL")
                  .containsIgnoringCase("insert");
            });
  }

  // ---- lesson c: the work of a run is all or nothing
  // ---------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-45 AC-8] a database error at {0}: no case, no event and no file of the run stays (one transaction)")
  @EnumSource(Failure.class)
  void nothingOfTheRunStays(Failure failure) throws Exception {
    JsonNode run = failedRun(failure);

    assertThat(RevalidationFixtures.casesOf(jdbc, NPI)).isZero();
    assertThat(RevalidationFixtures.outbox(jdbc, "RevalidationDue")).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT queue_file IS NULL FROM sweep_run WHERE public_id = ?::uuid",
                Boolean.class,
                run.path("runId").asText()))
        .isTrue();
  }

  @ParameterizedTest(
      name =
          "[MAM-45 AC-8] after a database error at {0} a re-run applies the work once: one case, one RevalidationDue, one record")
  @EnumSource(Failure.class)
  void reRunAppliesTheWorkOnce(Failure failure) throws Exception {
    failedRun(failure);
    RevalidationFixtures.dropFailures(jdbc);

    UUID rerun = RevalidationFixtures.sweep(mvc, "2026-03-31");

    assertThat(RevalidationFixtures.casesOf(jdbc, NPI)).isEqualTo(1);
    assertThat(RevalidationFixtures.outbox(jdbc, "RevalidationDue")).hasSize(1);
    JsonNode counts =
        RevalidationFixtures.read(
                mvc.perform(
                        MockMvcRequestBuilders.get(RevalidationFixtures.SWEEPS + "/{runId}", rerun)
                            .with(PemJwt.admin()))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .path("counts");
    assertThat(counts.path("queued").asInt()).isEqualTo(1);
    byte[] file =
        mvc.perform(
                MockMvcRequestBuilders.get(
                        RevalidationFixtures.SWEEPS + "/{runId}/queue-file", rerun)
                    .with(PemJwt.admin()))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(new String(file, StandardCharsets.ISO_8859_1)).startsWith(NPI + "20260228");
    assertThat(file).hasSize(80);
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-8] a failed run never blocks the next: the failed run stays FAILED and the new run COMPLETED")
  void failedRunStaysFailed() throws Exception {
    JsonNode failed = failedRun(Failure.CASE_INSERT);
    RevalidationFixtures.dropFailures(jdbc);

    UUID rerun = RevalidationFixtures.sweep(mvc, "2026-03-31");

    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM sweep_run WHERE public_id = ?::uuid",
                String.class,
                failed.path("runId").asText()))
        .isEqualTo("FAILED");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM sweep_run WHERE public_id = ?", String.class, rerun))
        .isEqualTo("COMPLETED");
  }
}
