package gov.medicaid.pem.tmsis.api;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.testsupport.PemJwt;
import gov.medicaid.pem.tmsis.ContractFile;
import gov.medicaid.pem.tmsis.IntegrationTest;
import gov.medicaid.pem.tmsis.LogCapture;
import gov.medicaid.pem.tmsis.TestClock;
import gov.medicaid.pem.tmsis.TestProviderService;
import gov.medicaid.pem.tmsis.TestProviderService.Fault;
import gov.medicaid.pem.tmsis.TestProviderService.Prov;
import gov.medicaid.pem.tmsis.TmsisApi;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * MAM-75 AC-6 (D-024): a failed run is visible and leaves nothing behind. provider-service answers
 * with an error, cuts the connection, sends something that is no provider page, or does not answer
 * in time: the run is FAILED with a non-empty error text and endedAt set, its file is 404, no
 * archive generation is added and no ExtractProduced event is published.
 *
 * <p>Code review lessons (2026-11-19) for services that run a job: a database error is reported
 * with a short reason, without SQL text, and the full exception is logged (a); the write that marks
 * work as done is in the transaction of the work, so a failure between leaves nothing (c); a call
 * to a service that does not answer ends in a visible FAILED run within the configured timeout,
 * never in a hang (d).
 *
 * <p>Time limits: the whole class is limited by JUnit, a run is awaited for 25 seconds at most, and
 * the slow provider-service answers only after 15 seconds, so a missing timeout fails a test, it
 * never hangs the build.
 */
@Timeout(300)
class FailedRunApiTest extends IntegrationTest {

  static final String DATE = "2026-03-31";
  static final String[] SQL_WORDS = {"select", "insert", "update", "sql"};

  static void twoActive() {
    TestProviderService.holds(Prov.active("1003000333"), Prov.active("1003000111"));
  }

  /**
   * The run failed: the BatchRun as listed, after the run has ended; fails the test if not FAILED.
   */
  JsonNode failedRun(String date) throws Exception {
    JsonNode run = api.runToEnd(date);
    assertThat(run.path("status").asText()).as("the run: " + run).isEqualTo("FAILED");
    return run;
  }

  void assertNothingLeftBehind(String runId) throws Exception {
    assertThat(api.fileStatus(runId)).as("file of the failed run").isEqualTo(404);
    assertThat(generations()).as("archive generations").isEmpty();
    assertThat(extractProducedEnvelopes()).as("ExtractProduced events").isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM extract_run WHERE status = 'COMPLETED'", Integer.class))
        .isZero();
  }

  // ---- AC-6: provider-service fails -------------------------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-75 AC-6] provider-service fails with {0}: the run is FAILED with an error text and endedAt, its file is 404, no generation, no event")
  @EnumSource(
      value = Fault.class,
      names = {"SERVER_ERROR", "CONNECTION_CUT", "NOT_A_PAGE", "NOT_JSON"})
  void providerFailureGivesAVisibleFailedRun(Fault fault) throws Exception {
    twoActive();
    TestProviderService.providerFails(fault);
    JsonNode run = failedRun(DATE);
    assertThat(run.path("error").asText()).isNotBlank();
    assertThat(run.path("endedAt").asText()).isNotBlank();
    assertThat(run.path("businessDate").asText()).isEqualTo(DATE);
    assertNothingLeftBehind(run.path("runId").asText());
  }

  @ParameterizedTest(
      name =
          "[MAM-75 AC-6] the failed run of {0} is a BatchRun of the contract, with error as a string and no archiveGeneration")
  @EnumSource(
      value = Fault.class,
      names = {"SERVER_ERROR", "CONNECTION_CUT"})
  void failedRunConformsToTheContract(Fault fault) throws Exception {
    twoActive();
    TestProviderService.providerFails(fault);
    JsonNode run = failedRun(DATE);
    assertThat(ContractFile.violations("BatchRun", run)).isEmpty();
    assertThat(run.path("counts").has("archiveGeneration")).isFalse();
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] provider-service fails after the first page of 200: the run is FAILED, nothing is archived or announced, rows stay 0")
  void failureAfterTheFirstPage() throws Exception {
    List<Prov> all = new ArrayList<>();
    for (int i = 1; i <= 450; i++) {
      all.add(Prov.active(String.format("1003%06d", 1000 + i)));
    }
    TestProviderService.holds(all);
    TestProviderService.providerFails(Fault.SERVER_ERROR, 1);
    JsonNode run = failedRun(DATE);
    assertThat(run.path("counts").path("rows").asInt()).isZero();
    assertThat(run.path("error").asText()).isNotBlank();
    assertNothingLeftBehind(run.path("runId").asText());
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] the token endpoint of the SYSTEM service account fails: the run is FAILED, not stuck in RUNNING")
  void tokenFailure() throws Exception {
    twoActive();
    TestProviderService.tokenFails(Fault.SERVER_ERROR);
    // a clock far ahead of every other test: the cached token has expired, a new one is asked for
    TestClock.set("2035-01-01T00:00:00Z");
    JsonNode run = failedRun(DATE);
    assertThat(run.path("error").asText()).isNotBlank();
    assertNothingLeftBehind(run.path("runId").asText());
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] after a failed run the next run for the same date works and has its file")
  void nextRunAfterAFailure() throws Exception {
    twoActive();
    TestProviderService.providerFails(Fault.SERVER_ERROR);
    failedRun(DATE);
    TestProviderService.providerRecovers();
    JsonNode run = api.runToEnd(DATE);
    assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(api.file(run.path("runId").asText())).hasSize(240);
    assertThat(generations()).containsExactly(1);
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] the failed run stays in the list as FAILED, with its error text, newest first")
  void failedRunStaysListed() throws Exception {
    twoActive();
    String ok = api.runToEnd(DATE).path("runId").asText();
    TestClock.set("2026-01-15T10:30:00Z");
    TestProviderService.providerFails(Fault.SERVER_ERROR);
    String failed = failedRun(DATE).path("runId").asText();
    JsonNode items = api.list(0, 20).path("items");
    assertThat(items).hasSize(2);
    assertThat(items.get(0).path("runId").asText()).isEqualTo(failed);
    assertThat(items.get(0).path("status").asText()).isEqualTo("FAILED");
    assertThat(items.get(1).path("runId").asText()).isEqualTo(ok);
    assertThat(items.get(1).path("status").asText()).isEqualTo("COMPLETED");
  }

  // ---- lesson d: a service that does not answer ------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-75 AC-6] provider-service does not answer: the run ends FAILED within the client timeout, long before the answer would have come (code review, lesson d)")
  void providerDoesNotAnswer() throws Exception {
    twoActive();
    TestProviderService.providerFails(Fault.SLOW);
    long start = System.nanoTime();
    JsonNode run = failedRun(DATE);
    Duration took = Duration.ofNanos(System.nanoTime() - start);
    assertThat(took).isLessThan(Duration.ofMillis(TestProviderService.SLOW_MILLIS - 3000));
    assertThat(run.path("error").asText()).isNotBlank();
    assertThat(run.path("endedAt").asText()).isNotBlank();
    assertNothingLeftBehind(run.path("runId").asText());
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] the token endpoint does not answer: the run ends FAILED within the client timeout, not RUNNING (code review, lesson d)")
  void tokenEndpointDoesNotAnswer() throws Exception {
    twoActive();
    TestProviderService.tokenFails(Fault.SLOW);
    TestClock.set("2035-01-01T00:00:00Z");
    long start = System.nanoTime();
    JsonNode run = failedRun(DATE);
    Duration took = Duration.ofNanos(System.nanoTime() - start);
    assertThat(took).isLessThan(Duration.ofMillis(TestProviderService.SLOW_MILLIS - 3000));
    assertNothingLeftBehind(run.path("runId").asText());
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] the POST answers 202 at once even when provider-service does not answer; only the run fails later (code review, lesson d)")
  void startIsNotHeldByASlowProvider() throws Exception {
    twoActive();
    TestProviderService.providerFails(Fault.SLOW);
    long start = System.nanoTime();
    MvcResult r = api.postBody(PemJwt.admin(), TmsisApi.body(DATE));
    Duration took = Duration.ofNanos(System.nanoTime() - start);
    assertThat(r.getResponse().getStatus()).isEqualTo(202);
    assertThat(took).isLessThan(Duration.ofSeconds(4));
    assertThat(api.read(r).path("status").asText()).isEqualTo("RUNNING");
    api.await(api.read(r).path("runId").asText());
  }

  // ---- lessons a and c: a database error --------------------------------------------------------

  /** The database operation that fails. */
  enum Failure {
    /** the insert of the extract rows (the extract step) */
    EXTRACT_ROWS,
    /** the insert of the archive generation (the archive step) */
    ARCHIVE_INSERT,
    /** the write that completes the run (after the archive row was inserted) */
    RUN_COMPLETE,
    /** the insert of the ExtractProduced event (after the run was completed) */
    OUTBOX_INSERT,
    /** the job repository when the job is started */
    JOB_LAUNCH
  }

  void inject(Failure failure) {
    switch (failure) {
      case EXTRACT_ROWS -> reject("reject_rows", "INSERT", "extract_row", "true");
      case ARCHIVE_INSERT -> reject("reject_archive", "INSERT", "extract_archive", "true");
      case RUN_COMPLETE ->
          reject("reject_complete", "UPDATE", "extract_run", "NEW.status = 'COMPLETED'");
      case OUTBOX_INSERT -> reject("reject_outbox", "INSERT", "outbox_event", "true");
      case JOB_LAUNCH -> reject("reject_job", "INSERT", "batch_job_instance", "true");
    }
  }

  /** What a caller can see of a run that failed on a database error. */
  record Seen(JsonNode answer, JsonNode listed, String stored, String runId) {

    /** The error texts shown to a caller: the 202 answer (when it has one) and the list. */
    List<String> shown() {
      List<String> texts = new ArrayList<>();
      if (!answer.path("error").isMissingNode() && !answer.path("error").isNull()) {
        texts.add(answer.path("error").asText());
      }
      texts.add(listed.path("error").asText());
      return texts;
    }
  }

  /** Makes the failure, runs the extract as ADMIN and reads the failed run back. */
  Seen failedOnDatabaseError(Failure failure) throws Exception {
    twoActive();
    inject(failure);
    MvcResult started = api.postBody(PemJwt.admin(), TmsisApi.body(DATE));
    assertThat(started.getResponse().getStatus())
        .as(started.getResponse().getContentAsString())
        .isEqualTo(202);
    JsonNode answer = api.read(started);
    String runId = answer.path("runId").asText();
    JsonNode listed = api.await(runId);
    assertThat(listed.path("status").asText())
        .as(failure + " must end the run FAILED, run: " + listed)
        .isEqualTo("FAILED");
    String stored =
        jdbc.queryForObject(
            "SELECT failure_reason FROM extract_run WHERE public_id = ?::uuid",
            String.class,
            runId);
    return new Seen(answer, listed, stored, runId);
  }

  @ParameterizedTest(
      name =
          "[MAM-75 AC-6] a database error at {0}: the error of the run has no SQL statement text (D-024, code review 2026-11-19, lesson a)")
  @EnumSource(Failure.class)
  void errorHasNoSqlText(Failure failure) throws Exception {
    Seen seen = failedOnDatabaseError(failure);
    assertThat(seen.shown())
        .allSatisfy(text -> assertThat(text.toLowerCase()).doesNotContain(SQL_WORDS));
  }

  @ParameterizedTest(
      name =
          "[MAM-75 AC-6] a database error at {0}: the error of the run is a short reason, not blank and at most 200 characters (lesson a)")
  @EnumSource(Failure.class)
  void errorIsShort(Failure failure) throws Exception {
    Seen seen = failedOnDatabaseError(failure);
    assertThat(seen.shown())
        .allSatisfy(text -> assertThat(text).isNotBlank().hasSizeLessThanOrEqualTo(200));
  }

  @ParameterizedTest(
      name =
          "[MAM-75 AC-6] a database error at {0}: the failure_reason stored with the run has no SQL statement text and is at most 200 characters (lesson a)")
  @EnumSource(Failure.class)
  void storedReasonIsShortAndHasNoSql(Failure failure) throws Exception {
    Seen seen = failedOnDatabaseError(failure);
    assertThat(seen.stored()).isNotBlank().hasSizeLessThanOrEqualTo(200);
    assertThat(seen.stored().toLowerCase()).doesNotContain(SQL_WORDS);
  }

  @ParameterizedTest(
      name =
          "[MAM-75 AC-6] a database error at {0}: the full exception is logged at ERROR, with the message of the database itself (D-024, lesson a)")
  @EnumSource(Failure.class)
  void fullExceptionIsLogged(Failure failure) throws Exception {
    try (LogCapture log = LogCapture.open()) {
      failedOnDatabaseError(failure);
      assertThat(log.lines())
          .filteredOn(l -> l.level() == Level.ERROR)
          .anySatisfy(
              l -> assertThat(l.everything()).contains("the test database rejected this write"));
      if (failure != Failure.JOB_LAUNCH) {
        assertThat(log.lines())
            .filteredOn(l -> l.level() == Level.ERROR && l.hasThrowable())
            .as("an ERROR event with the exception itself, not only its text")
            .isNotEmpty();
      }
    }
  }

  @ParameterizedTest(
      name =
          "[MAM-75 AC-6] a database error at {0}: no ExtractProduced event, no archive generation, no COMPLETED run and no file remain (lesson c)")
  @EnumSource(Failure.class)
  void nothingIsLeftBehind(Failure failure) throws Exception {
    Seen seen = failedOnDatabaseError(failure);
    assertNothingLeftBehind(seen.runId());
  }

  @ParameterizedTest(
      name =
          "[MAM-75 AC-6] a database error at {0}: after the failure the next run is archived as generation 1 and announced exactly once (lesson c)")
  @EnumSource(Failure.class)
  void nextRunAfterTheDatabaseError(Failure failure) throws Exception {
    Seen seen = failedOnDatabaseError(failure);
    lift(
        switch (failure) {
          case EXTRACT_ROWS -> "reject_rows";
          case ARCHIVE_INSERT -> "reject_archive";
          case RUN_COMPLETE -> "reject_complete";
          case OUTBOX_INSERT -> "reject_outbox";
          case JOB_LAUNCH -> "reject_job";
        });
    JsonNode next = api.runToEnd(DATE);
    assertThat(next.path("status").asText()).as(next.toString()).isEqualTo("COMPLETED");
    assertThat(next.path("counts").path("archiveGeneration").asInt()).isEqualTo(1);
    assertThat(generations()).containsExactly(1);
    assertThat(extractProducedEnvelopes()).hasSize(1);
    assertThat(extractProducedEnvelopes().get(0)).contains(next.path("runId").asText());
    assertThat(extractProducedEnvelopes().get(0)).doesNotContain(seen.runId());
    assertThat(api.file(next.path("runId").asText())).hasSize(240);
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] the job execution of a run that failed on a database error is FAILED as well (Spring Batch repository)")
  void jobExecutionIsFailed() throws Exception {
    Seen seen = failedOnDatabaseError(Failure.OUTBOX_INSERT);
    Awaitility.await("job execution of run " + seen.runId() + " has ended")
        .atMost(Duration.ofSeconds(20))
        .pollInterval(Duration.ofMillis(100))
        .until(() -> !"STARTED".equals(jobStatus(seen.runId())));
    assertThat(jobStatus(seen.runId())).isEqualTo("FAILED");
  }

  String jobStatus(String runId) {
    return jdbc.queryForObject(
        "SELECT e.status FROM batch_job_execution e JOIN batch_job_execution_params p"
            + " ON p.job_execution_id = e.job_execution_id"
            + " WHERE p.parameter_name = 'runId' AND p.parameter_value = ?",
        String.class,
        runId);
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] an enrolment date that is no date in provider-service's answer fails the run visibly: FAILED, a short reason, nothing archived")
  void invalidEnrolmentDate() throws Exception {
    TestProviderService.holds(Prov.active("1003000111").effective("31/12/2026"));
    JsonNode run = failedRun(DATE);
    assertThat(run.path("error").asText()).isNotBlank().hasSizeLessThanOrEqualTo(200);
    assertNothingLeftBehind(run.path("runId").asText());
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] a Medicaid ID longer than the 9 bytes of the record fails the run visibly with a short reason and no SQL text, nothing is cut or archived (D-024, lesson a)")
  void medicaidIdTooLong() throws Exception {
    TestProviderService.holds(Prov.active("1003000111").medicaid("M000001119999"));
    JsonNode run = failedRun(DATE);
    String error = run.path("error").asText();
    assertThat(error).isNotBlank().hasSizeLessThanOrEqualTo(200);
    assertThat(error.toLowerCase()).doesNotContain(SQL_WORDS);
    assertNothingLeftBehind(run.path("runId").asText());
  }
}
