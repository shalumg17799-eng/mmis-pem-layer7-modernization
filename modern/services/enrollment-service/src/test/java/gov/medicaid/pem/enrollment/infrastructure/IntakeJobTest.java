package gov.medicaid.pem.enrollment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.enrollment.AppInputFile;
import gov.medicaid.pem.enrollment.IntakeApi;
import gov.medicaid.pem.enrollment.IntakeIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The intake is a Spring Batch job (ADR-004, MAM-56): job PRVENR01 with one step "load" and the job
 * parameters runId, businessDate (D-020) and submittedBy, kept in the job repository that Flyway
 * created (V3). A run that completes ends its execution COMPLETED, a run that fails ends it FAILED,
 * and starting the same business date again is a new job instance (the run id identifies it), never
 * "already complete".
 */
class IntakeJobTest extends IntakeIntegrationTest {

  byte[] valid() {
    return AppInputFile.lines(AppInputFile.valid("1003000111"), AppInputFile.valid("1003000222"));
  }

  byte[] cutShort() {
    return AppInputFile.lines(
        AppInputFile.valid("1003000111"), AppInputFile.cut(AppInputFile.valid("1003000222"), 150));
  }

  /** The job executions of the run: job name, status of the execution. */
  List<Map<String, Object>> executionsOf(String runId) {
    return jdbc.queryForList(
        "SELECT i.job_name, e.status FROM batch_job_execution e"
            + " JOIN batch_job_instance i ON i.job_instance_id = e.job_instance_id"
            + " JOIN batch_job_execution_params p ON p.job_execution_id = e.job_execution_id"
            + " WHERE p.parameter_name = 'runId' AND p.parameter_value = ?",
        runId);
  }

  String run(byte[] file, String date) throws Exception {
    String id = IntakeApi.startAsAdmin(mvc, json, file, date);
    IntakeApi.await(mvc, json, id);
    backgroundJobsHaveEnded();
    return id;
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-1] a completed run is one execution of the Spring Batch job PRVENR01, COMPLETED")
  void completedRunIsAJobExecution() throws Exception {
    String id = run(valid(), DATE);
    assertThat(executionsOf(id))
        .extracting(row -> row.get("job_name"), row -> row.get("status"))
        .containsExactly(tuple("PRVENR01", "COMPLETED"));
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-1] the job parameters are runId, businessDate and submittedBy; the run id identifies the job instance")
  void jobParameters() throws Exception {
    String id = run(valid(), DATE);
    List<Map<String, Object>> params =
        jdbc.queryForList(
            "SELECT p.parameter_name, p.parameter_value, p.identifying FROM batch_job_execution_params p"
                + " WHERE p.job_execution_id = (SELECT p2.job_execution_id FROM batch_job_execution_params p2"
                + " WHERE p2.parameter_name = 'runId' AND p2.parameter_value = ?) ORDER BY p.parameter_name",
            id);
    assertThat(params)
        .extracting(
            row -> row.get("parameter_name"),
            row -> row.get("parameter_value"),
            row -> row.get("identifying"))
        .containsExactly(
            tuple("businessDate", DATE, "Y"),
            tuple("runId", id, "Y"),
            tuple("submittedBy", "admin1", "N"));
  }

  @Test
  @DisplayName("[MAM-56 AC-1] the job has one step, named load, that ends COMPLETED")
  void stepIsLoad() throws Exception {
    String id = run(valid(), DATE);
    assertThat(
            jdbc.queryForList(
                "SELECT s.step_name, s.status FROM batch_step_execution s"
                    + " JOIN batch_job_execution_params p ON p.job_execution_id = s.job_execution_id"
                    + " WHERE p.parameter_name = 'runId' AND p.parameter_value = ?",
                id))
        .extracting(row -> row.get("step_name"), row -> row.get("status"))
        .containsExactly(tuple("load", "COMPLETED"));
  }

  @Test
  @DisplayName("[MAM-57 AC-5] a run that fails ends its job execution FAILED")
  void failedRunIsAFailedExecution() throws Exception {
    String id = run(cutShort(), DATE);
    assertThat(executionsOf(id))
        .extracting(row -> row.get("job_name"), row -> row.get("status"))
        .containsExactly(tuple("PRVENR01", "FAILED"));
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-8] the same business date and file started again is a new job instance each time, and each is COMPLETED")
  void rerunIsANewJobInstance() throws Exception {
    String first = run(valid(), DATE);
    String second = run(valid(), DATE);
    assertThat(first).isNotEqualTo(second);
    assertThat(executionsOf(first)).hasSize(1);
    assertThat(executionsOf(second))
        .extracting(row -> row.get("status"))
        .containsExactly("COMPLETED");
    assertThat(count("SELECT count(*) FROM batch_job_instance WHERE job_name = 'PRVENR01'"))
        .isGreaterThanOrEqualTo(2);
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-1] the run result is the intake_run row, the job repository holds no counts of its own")
  void countsAreInTheRunRow() throws Exception {
    String id = run(valid(), DATE);
    JsonNode read = IntakeApi.run(mvc, json, id);
    assertThat(read.path("counts").path("read").asInt()).isEqualTo(2);
    assertThat(
            jdbc.queryForMap(
                "SELECT read_count, written_count, error_count, skipped_count FROM intake_run"
                    + " WHERE public_id = ?::uuid",
                id))
        .containsEntry("read_count", 2)
        .containsEntry("written_count", 2)
        .containsEntry("error_count", 0)
        .containsEntry("skipped_count", 0);
  }
}
