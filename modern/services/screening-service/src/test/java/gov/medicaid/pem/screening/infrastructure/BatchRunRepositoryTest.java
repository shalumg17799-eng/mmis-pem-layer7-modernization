package gov.medicaid.pem.screening.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gov.medicaid.pem.screening.Lane3Test;
import gov.medicaid.pem.screening.ScreeningWorld;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/**
 * MAM-32 as a Spring Batch job (ADR-004): the job PRVSCR01 with its job parameters runId and
 * businessDate (D-020), its steps screen and results and the counts Spring Batch keeps; a failed
 * run is a failed job. The settings of the sources (Q-039 A: 2 seconds, one retry) are checked too.
 */
class BatchRunRepositoryTest extends Lane3Test {

  static final String DATE = "2026-03-31";

  @Autowired Environment env;

  void providers() {
    ObjectNode a = ScreeningWorld.individual("1003000111", "TESTPROV", "ALPHA", "MD12345", "TX");
    ObjectNode b = ScreeningWorld.individual("1003000555", "TESTPROV", "ECHO", "MD12345", "TX");
    ObjectNode c = ScreeningWorld.individual("1003000222", "TESTPROV", "BRAVO", "MD22222", "TX");
    ObjectNode d = ScreeningWorld.individual("1003000444", "TESTPROV", "DELTA", "MD44444", "TX");
    ScreeningWorld.lists(
        List.of(
            ScreeningWorld.withFlag(a, "U"),
            ScreeningWorld.withFlag(b, "U"),
            ScreeningWorld.withFlag(c, "P"),
            ScreeningWorld.withFlag(d, "F")));
    ScreeningWorld.licence("MD12345", "TX", "ACTIVE", "2028-09");
  }

  /** The job execution of the run: its status, once the job repository has written it. */
  String jobStatus(String runId) {
    until(
        "the job repository has the execution of run " + runId + " finished",
        () -> {
          List<String> s = jobStatuses(runId);
          return !s.isEmpty() && !s.get(0).equals("STARTING") && !s.get(0).equals("STARTED");
        },
        BOUND);
    return jobStatuses(runId).get(0);
  }

  List<String> jobStatuses(String runId) {
    return jdbc.queryForList(
        "SELECT e.status FROM batch_job_execution e"
            + " JOIN batch_job_instance i ON i.job_instance_id = e.job_instance_id"
            + " JOIN batch_job_execution_params p ON p.job_execution_id = e.job_execution_id"
            + " WHERE i.job_name = 'PRVSCR01' AND p.parameter_name = 'runId' AND p.parameter_value = ?",
        String.class,
        runId);
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-1] a run is the job PRVSCR01 with the job parameters runId and businessDate")
  void jobParameters() throws Exception {
    providers();
    String runId = runToEnd(DATE).path("runId").asText();
    jobStatus(runId);
    Map<String, Object> date =
        jdbc.queryForMap(
            "SELECT p.parameter_value, p.parameter_type FROM batch_job_execution_params p"
                + " JOIN batch_job_execution_params r ON r.job_execution_id = p.job_execution_id"
                + " WHERE r.parameter_name = 'runId' AND r.parameter_value = ?"
                + " AND p.parameter_name = 'businessDate'",
            runId);
    assertThat(date.get("parameter_value")).isEqualTo(DATE);
    assertThat(String.valueOf(date.get("parameter_type"))).contains("LocalDate");
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-5] a completed run is a COMPLETED job with the steps screen (4 read, 2 filtered out) and results, both COMPLETED")
  void completedJob() throws Exception {
    providers();
    JsonNode run = runToEnd(DATE);
    String runId = run.path("runId").asText();
    assertThat(jobStatus(runId)).isEqualTo("COMPLETED");
    List<Map<String, Object>> steps =
        jdbc.queryForList(
            "SELECT s.step_name, s.status, s.read_count, s.filter_count FROM batch_step_execution s"
                + " JOIN batch_job_execution_params p ON p.job_execution_id = s.job_execution_id"
                + " WHERE p.parameter_name = 'runId' AND p.parameter_value = ?"
                + " ORDER BY s.step_execution_id",
            runId);
    assertThat(steps.stream().map(s -> s.get("step_name")).toList())
        .containsExactly("screen", "results");
    assertThat(steps).allSatisfy(s -> assertThat(s.get("status")).isEqualTo("COMPLETED"));
    assertThat(((Number) steps.get(0).get("read_count")).intValue()).isEqualTo(4);
    assertThat(((Number) steps.get(0).get("filter_count")).intValue()).isEqualTo(2);
    assertThat(run.path("counts").path("read").asInt()).isEqualTo(4);
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-6] a run that fails is a FAILED job: the step screen is FAILED and the step results never ran")
  void failedJob() throws Exception {
    ScreeningWorld.listAnswers(503);
    JsonNode run = runToEnd(DATE);
    String runId = run.path("runId").asText();
    assertThat(run.path("status").asText()).isEqualTo("FAILED");
    assertThat(jobStatus(runId)).isEqualTo("FAILED");
    List<Map<String, Object>> steps =
        jdbc.queryForList(
            "SELECT s.step_name, s.status FROM batch_step_execution s"
                + " JOIN batch_job_execution_params p ON p.job_execution_id = s.job_execution_id"
                + " WHERE p.parameter_name = 'runId' AND p.parameter_value = ?"
                + " ORDER BY s.step_execution_id",
            runId);
    assertThat(steps).hasSize(1);
    assertThat(steps.get(0).get("step_name")).isEqualTo("screen");
    assertThat(steps.get(0).get("status")).isEqualTo("FAILED");
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-7] two runs for the same business date are two job executions, each with its own run id")
  void rerunIsANewJobExecution() throws Exception {
    providers();
    String first = runToEnd(DATE).path("runId").asText();
    String second = runToEnd(DATE).path("runId").asText();
    assertThat(jobStatus(first)).isEqualTo("COMPLETED");
    assertThat(jobStatus(second)).isEqualTo("COMPLETED");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(DISTINCT p.job_execution_id) FROM batch_job_execution_params p"
                    + " WHERE p.parameter_name = 'runId' AND p.parameter_value IN (?, ?)",
                Integer.class,
                first,
                second))
        .isEqualTo(2);
  }

  // ---- the settings of the sources (question C-1 A) ---------------------------------------

  @Test
  @DisplayName(
      "[MAM-20 AC-2] a source has a limit of 2 seconds and one retry unless configured otherwise (Q-039 A)")
  void sourceSettings() {
    assertThat(env.getProperty("pem.screening.source-timeout")).isEqualTo("2s");
    assertThat(env.getProperty("pem.screening.source-retries", Integer.class)).isEqualTo(1);
  }
}
