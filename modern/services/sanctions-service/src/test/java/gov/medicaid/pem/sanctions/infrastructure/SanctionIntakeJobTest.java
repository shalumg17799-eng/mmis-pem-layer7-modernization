package gov.medicaid.pem.sanctions.infrastructure;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.sanctions.IntakeApi;
import gov.medicaid.pem.sanctions.IntegrationTest;
import gov.medicaid.pem.sanctions.SancinFile;
import gov.medicaid.pem.sanctions.TestProviderService;
import gov.medicaid.pem.sanctions.application.SanctionIntake;
import gov.medicaid.pem.sanctions.application.SanctionIntakeLauncher;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MAM-55: the Spring Batch job PRVSAN01 (ADR-004) in the job repository of sanctions_db: its name,
 * its parameters runId and businessDate (D-020), its step "apply", the job that fails (AC-6), and
 * the re-run of a business date (AC-7) as a job execution of its own. The run table and the job
 * repository are both read from the real database.
 */
class SanctionIntakeJobTest extends IntegrationTest {

  static final String REASON = "SYNTHETIC TEST REASON";
  static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 3, 31);

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired SanctionIntake intake;
  @Autowired SanctionIntakeLauncher launcher;

  @BeforeEach
  void providers() {
    jdbc.update("DELETE FROM sanction");
    jdbc.update("DELETE FROM sanction_intake_run");
    jdbc.update("DELETE FROM outbox_event");
    TestProviderService.reset();
    TestProviderService.holds("1003000111", "A");
    TestProviderService.holds("1003000333", "A");
    TestProviderService.holds("1003000222", "S");
  }

  static String rec(String npi, String action) {
    return SancinFile.record(npi, action, REASON, "20260331");
  }

  byte[] threeRecords() {
    return SancinFile.lines(rec("1003000111", "T"), rec("1003000333", "S"), rec("1003000222", "R"));
  }

  /** The job execution of the run: status, name and id. */
  Map<String, Object> job(String runId) {
    return jdbc.queryForMap(
        "SELECT e.status AS status, i.job_name AS job_name, e.job_execution_id AS id"
            + " FROM batch_job_execution e"
            + " JOIN batch_job_instance i ON i.job_instance_id = e.job_instance_id"
            + " JOIN batch_job_execution_params p ON p.job_execution_id = e.job_execution_id"
            + " WHERE p.parameter_name = 'runId' AND p.parameter_value = ?",
        runId);
  }

  /** The job repository is updated a moment after the run table, so its status is awaited. */
  Map<String, Object> jobEnded(String runId, String status) {
    Awaitility.await("job of run " + runId + " is " + status)
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(() -> assertThat(job(runId).get("status")).isEqualTo(status));
    return job(runId);
  }

  String parameter(Object executionId, String name) {
    return jdbc.queryForObject(
        "SELECT parameter_value FROM batch_job_execution_params"
            + " WHERE job_execution_id = ? AND parameter_name = ?",
        String.class,
        executionId,
        name);
  }

  // ---- the job of a normal run -----------------------------------------------------------------

  @Test
  @DisplayName("[MAM-55 AC-1] a run is the Spring Batch job PRVSAN01, COMPLETED")
  void jobNameAndStatus() throws Exception {
    String runId = IntakeApi.startAsAdmin(mvc, json, threeRecords(), "2026-03-31");
    IntakeApi.await(mvc, json, runId);
    Map<String, Object> job = jobEnded(runId, "COMPLETED");
    assertThat(job.get("job_name")).isEqualTo("PRVSAN01");
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-1] the job parameters are the run id, the business date 2026-03-31 (D-020) and who started it")
  void jobParameters() throws Exception {
    String runId = IntakeApi.startAsAdmin(mvc, json, threeRecords(), "2026-03-31");
    IntakeApi.await(mvc, json, runId);
    Object execution = job(runId).get("id");
    assertThat(parameter(execution, "runId")).isEqualTo(runId);
    assertThat(parameter(execution, "businessDate")).isEqualTo("2026-03-31");
    assertThat(parameter(execution, "recordedBy")).isEqualTo("admin1");
  }

  @Test
  @DisplayName("[MAM-55 AC-1] the job has the one step 'apply', COMPLETED")
  void step() throws Exception {
    String runId = IntakeApi.startAsAdmin(mvc, json, threeRecords(), "2026-03-31");
    IntakeApi.await(mvc, json, runId);
    jobEnded(runId, "COMPLETED");
    List<Map<String, Object>> steps =
        jdbc.queryForList(
            "SELECT step_name, status FROM batch_step_execution WHERE job_execution_id = ?",
            job(runId).get("id"));
    assertThat(steps).hasSize(1);
    assertThat(steps.get(0))
        .containsEntry("step_name", "apply")
        .containsEntry("status", "COMPLETED");
  }

  // ---- AC-6: a job that fails ----------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-55 AC-6] an unreadable record fails the job execution and its step as well as the run")
  void failedJob() throws Exception {
    byte[] file =
        SancinFile.lines(
            rec("1003000111", "T"),
            SancinFile.cut(rec("1003000333", "S"), 60),
            rec("1003000222", "R"));
    String runId = IntakeApi.startAsAdmin(mvc, json, file, "2026-03-31");
    JsonNode run = IntakeApi.await(mvc, json, runId);
    assertThat(run.path("status").asText()).isEqualTo("FAILED");
    jobEnded(runId, "FAILED");
    List<String> steps =
        jdbc.queryForList(
            "SELECT status FROM batch_step_execution WHERE job_execution_id = ?",
            String.class,
            job(runId).get("id"));
    assertThat(steps).containsExactly("FAILED");
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-6] the job cannot be started twice for the same run: the launcher says so instead of leaving the run RUNNING")
  void launchTwice() throws Exception {
    UUID id = intake.open(BUSINESS_DATE);
    launcher.launch(id, BUSINESS_DATE, threeRecords(), "admin1");
    JsonNode run = IntakeApi.await(mvc, json, id.toString());
    assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
    jobEnded(id.toString(), "COMPLETED");
    assertThatThrownBy(() -> launcher.launch(id, BUSINESS_DATE, threeRecords(), "admin1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PRVSAN01 not started");
  }

  // ---- AC-7: re-runs --------------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-55 AC-7] running the same date again is a second job execution of its own")
  void rerunIsANewJob() throws Exception {
    String a = IntakeApi.startAsAdmin(mvc, json, threeRecords(), "2026-03-31");
    IntakeApi.await(mvc, json, a);
    String b = IntakeApi.startAsAdmin(mvc, json, threeRecords(), "2026-03-31");
    IntakeApi.await(mvc, json, b);
    assertThat(a).isNotEqualTo(b);
    assertThat(job(a).get("id")).isNotEqualTo(job(b).get("id"));
    assertThat(jobEnded(b, "COMPLETED").get("job_name")).isEqualTo("PRVSAN01");
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-7] after a FAILED run the corrected file for the same date applies only what was not applied")
  void rerunAfterAFailure() throws Exception {
    byte[] broken =
        SancinFile.lines(
            rec("1003000111", "T"),
            SancinFile.cut(rec("1003000333", "S"), 60),
            rec("1003000222", "R"));
    JsonNode failed = IntakeApi.runToEnd(mvc, json, broken, "2026-03-31");
    assertThat(failed.path("status").asText()).isEqualTo("FAILED");

    JsonNode rerun = IntakeApi.runToEnd(mvc, json, threeRecords(), "2026-03-31");
    assertThat(rerun.path("status").asText()).isEqualTo("COMPLETED");
    JsonNode counts = rerun.path("counts");
    assertThat(counts.path("read").asInt()).isEqualTo(3);
    assertThat(counts.path("applied").asInt()).isEqualTo(2);
    assertThat(counts.path("skipped").asInt()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM sanction", Integer.class)).isEqualTo(3);
    assertThat(
            TestProviderService.MOCK.findAll(
                postRequestedFor(urlPathEqualTo("/api/v1/providers/1003000111/status-changes"))))
        .hasSize(1);
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-7] the same file for another business date is not a re-run: its records apply again")
  void otherBusinessDate() throws Exception {
    JsonNode first = IntakeApi.runToEnd(mvc, json, threeRecords(), "2026-03-31");
    assertThat(first.path("counts").path("applied").asInt()).isEqualTo(3);
    JsonNode next = IntakeApi.runToEnd(mvc, json, threeRecords(), "2026-04-01");
    assertThat(next.path("counts").path("applied").asInt()).isEqualTo(3);
    assertThat(next.path("counts").path("skipped").asInt()).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM sanction", Integer.class)).isEqualTo(6);
  }
}
