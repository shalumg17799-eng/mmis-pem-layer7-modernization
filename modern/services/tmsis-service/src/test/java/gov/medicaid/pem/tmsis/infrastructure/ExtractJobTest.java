package gov.medicaid.pem.tmsis.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.tmsis.IntegrationTest;
import gov.medicaid.pem.tmsis.TestProviderService;
import gov.medicaid.pem.tmsis.TestProviderService.Fault;
import gov.medicaid.pem.tmsis.TestProviderService.Prov;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * MAM-71 and MAM-75 (ADR-004): a run is the Spring Batch job PRVTMS01 in the job repository of
 * tmsis_db, with the parameters runId and businessDate (D-020), the steps "extract"
 * (provider-service to TMSISOUT rows, with the read, filter and write counts of the job repository)
 * and "archive" (the file as the next generation); a run that fails leaves a FAILED job execution.
 */
@Timeout(120)
class ExtractJobTest extends IntegrationTest {

  static final String DATE = "2026-03-31";

  /**
   * The job execution of the run: status, and its id. The run is COMPLETED (or FAILED) when its
   * last step commits, a moment before the job repository records the end of the job, so this waits
   * for the job to end (20 seconds at most) before it reads the status.
   */
  Map<String, Object> job(String runId) {
    Awaitility.await("job execution of run " + runId + " has ended")
        .atMost(Duration.ofSeconds(20))
        .pollInterval(Duration.ofMillis(100))
        .until(() -> !List.of("STARTING", "STARTED").contains(jobRow(runId).get("status")));
    return jobRow(runId);
  }

  Map<String, Object> jobRow(String runId) {
    return jdbc.queryForMap(
        "SELECT e.status AS status, i.job_name AS job_name, e.job_execution_id AS id"
            + " FROM batch_job_execution e"
            + " JOIN batch_job_instance i ON i.job_instance_id = e.job_instance_id"
            + " JOIN batch_job_execution_params p ON p.job_execution_id = e.job_execution_id"
            + " WHERE p.parameter_name = 'runId' AND p.parameter_value = ?",
        runId);
  }

  String parameter(Object executionId, String name) {
    return jdbc.queryForObject(
        "SELECT parameter_value FROM batch_job_execution_params"
            + " WHERE job_execution_id = ? AND parameter_name = ?",
        String.class,
        executionId,
        name);
  }

  Map<String, Object> step(Object executionId, String name) {
    return jdbc.queryForMap(
        "SELECT status, read_count, filter_count, write_count FROM batch_step_execution"
            + " WHERE job_execution_id = ? AND step_name = ?",
        executionId,
        name);
  }

  String runWith(List<Prov> providers, String date) throws Exception {
    TestProviderService.holds(providers);
    return api.runToEnd(date).path("runId").asText();
  }

  @Test
  @DisplayName("[MAM-75 AC-1] a run is the Spring Batch job PRVTMS01, COMPLETED")
  void jobNameAndStatus() throws Exception {
    Map<String, Object> job = job(runWith(List.of(Prov.active("1003000111")), DATE));
    assertThat(job.get("job_name")).isEqualTo("PRVTMS01");
    assertThat(job.get("status")).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-3] the job parameters are the run id and the explicit business date 2026-03-31 (D-020)")
  void jobParameters() throws Exception {
    String runId = runWith(List.of(Prov.active("1003000111")), DATE);
    Object execution = job(runId).get("id");
    assertThat(parameter(execution, "runId")).isEqualTo(runId);
    assertThat(parameter(execution, "businessDate")).isEqualTo(DATE);
  }

  @Test
  @DisplayName("[MAM-71 AC-1] the job has the steps extract and archive, both COMPLETED")
  void twoSteps() throws Exception {
    Object execution = job(runWith(List.of(Prov.active("1003000111")), DATE)).get("id");
    List<String> steps =
        jdbc.queryForList(
            "SELECT step_name || ':' || status FROM batch_step_execution WHERE job_execution_id = ?"
                + " ORDER BY step_execution_id",
            String.class,
            execution);
    assertThat(steps).containsExactly("extract:COMPLETED", "archive:COMPLETED");
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-1] the extract step counts what it read, filtered and wrote: 6 providers read, 4 filtered out (not ACTIVE), 2 written")
  void extractStepCounts() throws Exception {
    TestProviderService.ignoreStatusFilter();
    String runId =
        runWith(
            List.of(
                Prov.active("1003000333"),
                Prov.of("1003000444", "D"),
                Prov.of("1003000555", "P"),
                Prov.active("1003000111"),
                Prov.of("1003000777", "S"),
                Prov.of("1003000888", "T")),
            DATE);
    Map<String, Object> extract = step(job(runId).get("id"), "extract");
    assertThat(((Number) extract.get("read_count")).intValue()).isEqualTo(6);
    assertThat(((Number) extract.get("filter_count")).intValue()).isEqualTo(4);
    assertThat(((Number) extract.get("write_count")).intValue()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] 450 providers: the extract step reads 450, filters none and writes 450")
  void extractStepCountsForManyProviders() throws Exception {
    List<Prov> all = new ArrayList<>();
    for (int i = 1; i <= 450; i++) {
      all.add(Prov.active(String.format("1003%06d", 1000 + i)));
    }
    Map<String, Object> extract = step(job(runWith(all, DATE)).get("id"), "extract");
    assertThat(((Number) extract.get("read_count")).intValue()).isEqualTo(450);
    assertThat(((Number) extract.get("filter_count")).intValue()).isZero();
    assertThat(((Number) extract.get("write_count")).intValue()).isEqualTo(450);
  }

  @Test
  @DisplayName("[MAM-75 AC-4] running again for the same date is a second job execution of its own")
  void rerunIsANewJob() throws Exception {
    TestProviderService.holds(Prov.active("1003000111"));
    String a = api.runToEnd(DATE).path("runId").asText();
    String b = api.runToEnd(DATE).path("runId").asText();
    assertThat(job(a).get("id")).isNotEqualTo(job(b).get("id"));
    assertThat(job(b).get("status")).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] when provider-service fails the job execution is FAILED, its extract step is FAILED and its archive step never ran")
  void failedJob() throws Exception {
    TestProviderService.holds(Prov.active("1003000111"));
    TestProviderService.providerFails(Fault.SERVER_ERROR);
    JsonNode run = api.runToEnd(DATE);
    Object execution = job(run.path("runId").asText()).get("id");
    assertThat(job(run.path("runId").asText()).get("status")).isEqualTo("FAILED");
    assertThat(step(execution, "extract").get("status")).isEqualTo("FAILED");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM batch_step_execution WHERE job_execution_id = ?"
                    + " AND step_name = 'archive'",
                Integer.class,
                execution))
        .isZero();
  }
}
