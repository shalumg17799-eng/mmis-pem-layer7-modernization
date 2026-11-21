package gov.medicaid.pem.tmsis.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.tmsis.ContractFile;
import gov.medicaid.pem.tmsis.IntegrationTest;
import gov.medicaid.pem.tmsis.ServiceRestart;
import gov.medicaid.pem.tmsis.TestClock;
import gov.medicaid.pem.tmsis.TestProviderService;
import gov.medicaid.pem.tmsis.TestProviderService.Fault;
import gov.medicaid.pem.tmsis.TestProviderService.Prov;
import gov.medicaid.pem.tmsis.application.ExtractRuns;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * MAM-75 AC-6 and its business requirement ("if a run fails, the failure is visible"), D-024, code
 * review of tmsis-service 2026-11-20, MUST-FIX 2: a run that was RUNNING when tmsis-service stopped
 * (a deploy, an out-of-memory kill, a restart of the shared JVM) must not stay RUNNING for ever.
 * The thread that runs the job is the only thing that moves a run off RUNNING, and it dies with the
 * service, so the next start of the service has to: every RUNNING run becomes FAILED with endedAt
 * and a short reason, its STARTED job execution of the job repository is no longer STARTED, and
 * nothing is archived or announced for it. One instance per tmsis_db is assumed (as in docker
 * compose; the assumption is for ADR-004).
 *
 * <p>The service is "started" the way Spring Boot starts it: ApplicationReadyEvent is announced to
 * the context ({@link ServiceRestart#startsAgain}); the reviewer's fix is a listener of that event.
 * The state a stop leaves is written by {@link ServiceRestart#crashedDuringRun}. Nothing here waits
 * for ever: a run is awaited for 25 seconds at most and the class is limited by JUnit.
 */
@Timeout(300)
class InterruptedRunRecoveryTest extends IntegrationTest {

  static final String DATE = "2026-03-31";
  static final String REASON = "interrupted: tmsis-service stopped during the run";

  @Autowired ExtractRuns runs;
  @Autowired ConfigurableApplicationContext context;

  /**
   * What a test left RUNNING (when the recovery does not exist) is ended here, after its
   * assertions, so that it cannot hold the next test for the base class's 30 seconds.
   */
  @AfterEach
  void endWhatTheTestLeftRunning() {
    ServiceRestart.endWhatIsLeft(jdbc);
  }

  UUID crashed(String date) {
    return ServiceRestart.crashedDuringRun(jdbc, runs, date, true);
  }

  /**
   * The service starts again; the run is listed once it has left RUNNING, or after 8 seconds as it
   * is (the recovery is a listener of the event, it needs no more time), so that a missing recovery
   * fails the test with the status RUNNING in its message, never by hanging.
   */
  JsonNode afterTheRestart(UUID runId) throws Exception {
    ServiceRestart.startsAgain(context);
    return awaitLeftRunning(runId);
  }

  JsonNode awaitLeftRunning(UUID runId) throws Exception {
    try {
      Awaitility.await("the run " + runId + " has left RUNNING")
          .atMost(Duration.ofSeconds(8))
          .pollInterval(Duration.ofMillis(100))
          .until(() -> !"RUNNING".equals(api.listed(runId.toString()).path("status").asText()));
    } catch (ConditionTimeoutException e) {
      // the assertions that follow show the status
    }
    return api.listed(runId.toString());
  }

  Map<String, Object> runRow(UUID runId) {
    return jdbc.queryForMap(
        "SELECT status, failure_reason, ended_at, version, row_count FROM extract_run"
            + " WHERE public_id = ?",
        runId);
  }

  // ---- the run ----------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-75 AC-6] a run that was RUNNING when tmsis-service stopped is FAILED after the next start, with the reason 'interrupted: tmsis-service stopped during the run' and endedAt")
  void runIsFailedWithTheReason() throws Exception {
    UUID runId = crashed(DATE);
    JsonNode run = afterTheRestart(runId);
    assertThat(run.path("status").asText()).as("the run: %s", run).isEqualTo("FAILED");
    assertThat(run.path("error").asText()).isEqualTo(REASON);
    assertThat(Instant.parse(run.path("endedAt").asText())).isEqualTo(TestClock.DEFAULT);
    assertThat(Instant.parse(run.path("startedAt").asText())).isEqualTo(ServiceRestart.STARTED_AT);
    assertThat(run.path("businessDate").asText()).isEqualTo(DATE);
    assertThat(run.path("counts").path("rows").asInt()).isZero();
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] the interrupted run is a BatchRun of the contract, with error as a string and no archiveGeneration")
  void interruptedRunConformsToTheContract() throws Exception {
    JsonNode run = afterTheRestart(crashed(DATE));
    assertThat(run.path("status").asText()).as("the run: %s", run).isEqualTo("FAILED");
    assertThat(ContractFile.violations("BatchRun", run)).isEmpty();
    assertThat(run.path("counts").has("archiveGeneration")).isFalse();
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] the job execution of the run is FAILED or ABANDONED with an end time, no longer STARTED")
  void jobExecutionIsNotLeftStarted() throws Exception {
    UUID runId = crashed(DATE);
    afterTheRestart(runId);
    assertThat(ServiceRestart.jobStatus(jdbc, runId)).isIn("FAILED", "ABANDONED");
    assertThat(ServiceRestart.jobEnded(jdbc, runId))
        .as("the job execution has an end time")
        .isTrue();
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] a run whose job never started (the stop came between recording the run and starting the job) is FAILED as well, not RUNNING for ever")
  void runWithoutAJobExecutionIsFailedToo() throws Exception {
    UUID runId = ServiceRestart.crashedDuringRun(jdbc, runs, DATE, false);
    JsonNode run = afterTheRestart(runId);
    assertThat(run.path("status").asText()).as("the run: %s", run).isEqualTo("FAILED");
    assertThat(run.path("error").asText()).isEqualTo(REASON);
    assertThat(run.path("endedAt").asText()).isNotBlank();
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] every RUNNING run is failed, not only one: three runs that were RUNNING give three FAILED runs")
  void everyRunningRunIsFailed() throws Exception {
    List<UUID> ids = List.of(crashed("2026-01-31"), crashed("2026-02-28"), crashed(DATE));
    ServiceRestart.startsAgain(context);
    for (UUID id : ids) {
      JsonNode run = awaitLeftRunning(id);
      assertThat(run.path("status").asText()).as("the run: %s", run).isEqualTo("FAILED");
      assertThat(run.path("error").asText()).isEqualTo(REASON);
      assertThat(ServiceRestart.jobStatus(jdbc, id)).isIn("FAILED", "ABANDONED");
    }
  }

  // ---- nothing is archived or announced
  // ----------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-75 AC-6] the interrupted run has no file, adds no archive generation and publishes no ExtractProduced event; the earlier run's generation and event are as they were")
  void nothingIsArchivedOrAnnounced() throws Exception {
    TestProviderService.holds(Prov.active("1003000111"), Prov.active("1003000333"));
    String earlier = api.runToEnd("2026-02-28").path("runId").asText();
    byte[] earlierFile = api.file(earlier);
    UUID runId = crashed(DATE);
    JsonNode run = afterTheRestart(runId);
    assertThat(run.path("status").asText()).as("the run: %s", run).isEqualTo("FAILED");
    assertThat(api.fileStatus(runId.toString())).isEqualTo(404);
    assertThat(generations()).containsExactly(1);
    assertThat(extractProducedEnvelopes()).hasSize(1);
    assertThat(api.file(earlier)).isEqualTo(earlierFile);
  }

  // ---- what is not touched
  // ------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-75 AC-6] a COMPLETED run and a FAILED run are not touched by the start: status, reason, endedAt and version stay as they were")
  void endedRunsAreNotTouched() throws Exception {
    TestProviderService.holds(Prov.active("1003000111"), Prov.active("1003000333"));
    UUID completed = UUID.fromString(api.runToEnd("2026-02-28").path("runId").asText());
    TestProviderService.providerFails(Fault.SERVER_ERROR);
    UUID failed = UUID.fromString(api.runToEnd("2026-03-15").path("runId").asText());
    Map<String, Object> completedBefore = runRow(completed);
    Map<String, Object> failedBefore = runRow(failed);
    assertThat(completedBefore.get("status")).isEqualTo("COMPLETED");
    assertThat(failedBefore.get("status")).isEqualTo("FAILED");
    UUID interrupted = crashed(DATE);
    afterTheRestart(interrupted);
    assertThat(runRow(completed)).isEqualTo(completedBefore);
    assertThat(runRow(failed)).isEqualTo(failedBefore);
    assertThat(runRow(interrupted).get("status")).isEqualTo("FAILED");
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] a second start does not change the run that the first start failed: endedAt, reason and version stay")
  void secondStartKeepsTheFirstResult() throws Exception {
    UUID runId = crashed(DATE);
    afterTheRestart(runId);
    Map<String, Object> first = runRow(runId);
    assertThat(first.get("status")).isEqualTo("FAILED");
    TestClock.set("2026-01-15T11:30:00Z");
    ServiceRestart.startsAgain(context);
    assertThat(runRow(runId)).isEqualTo(first);
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] after the recovery the next run for the same date works: COMPLETED, with its file as generation 1 (the interrupted run took no generation)")
  void nextRunWorksAfterTheRecovery() throws Exception {
    UUID runId = crashed(DATE);
    JsonNode interrupted = afterTheRestart(runId);
    assertThat(interrupted.path("status").asText()).isEqualTo("FAILED");
    TestProviderService.holds(Prov.active("1003000111"), Prov.active("1003000333"));
    JsonNode next = api.runToEnd(DATE);
    assertThat(next.path("status").asText()).as("the run: %s", next).isEqualTo("COMPLETED");
    assertThat(next.path("counts").path("archiveGeneration").asInt()).isEqualTo(1);
    assertThat(api.file(next.path("runId").asText())).hasSize(240);
    assertThat(generations()).containsExactly(1);
  }
}
