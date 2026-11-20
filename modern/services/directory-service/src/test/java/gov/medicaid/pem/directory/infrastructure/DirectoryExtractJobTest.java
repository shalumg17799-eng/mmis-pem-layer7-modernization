package gov.medicaid.pem.directory.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.directory.DirectoryFixtures;
import gov.medicaid.pem.directory.DirectoryFixtures.Row;
import gov.medicaid.pem.directory.IntegrationTest;
import gov.medicaid.pem.testsupport.PemJwt;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * MAM-67: the Spring Batch job PRVDIR01 (ADR-004) in the job repository of directory_db: its name,
 * its parameters runId and businessDate (D-020), its step "extract" with the write count equal to
 * the records, and (AC-7) a job that fails: the run is FAILED with a reason and has no file, while
 * the job execution is FAILED too. The failure is a real one: the test database rejects the write
 * that completes a run.
 */
class DirectoryExtractJobTest extends IntegrationTest {

  static final String BASE = "/api/v1/batch/directory-extracts";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  @BeforeEach
  void directory() {
    DirectoryFixtures.replaceAll(
        jdbc,
        Row.individual(
            "1003000111", "M00000111", "L", "A", "207Q00000X", "MARIA", "RIVERA", null, null),
        Row.organization(
            "1003000333", "M00000333", "FA", "H", "A", "251G00000X", "SUNSET HOSPICE INC"));
  }

  @AfterEach
  void removeRejectingTrigger() {
    jdbc.execute("DROP TRIGGER IF EXISTS reject_completed_run ON extract_run");
    jdbc.execute("DROP FUNCTION IF EXISTS reject_completed_run()");
  }

  JsonNode start(String date) throws Exception {
    MvcResult r =
        mvc.perform(
                post(BASE)
                    .with(PemJwt.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"businessDate\":\"" + date + "\"}"))
            .andReturn();
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(202);
    return json.readTree(r.getResponse().getContentAsString());
  }

  /** The job execution of the run: status, and its parameters by name. */
  Map<String, Object> job(String runId) {
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

  void rejectCompletedRuns() {
    jdbc.execute(
        "CREATE FUNCTION reject_completed_run() RETURNS trigger AS $$ BEGIN"
            + " RAISE EXCEPTION 'extract file rejected by the test database'; END; $$"
            + " LANGUAGE plpgsql");
    jdbc.execute(
        "CREATE TRIGGER reject_completed_run BEFORE UPDATE ON extract_run FOR EACH ROW"
            + " WHEN (NEW.status = 'COMPLETED') EXECUTE FUNCTION reject_completed_run()");
  }

  // ---- the job of a normal run ----------------------------------------------------------------

  @Test
  @DisplayName("[MAM-67 AC-1] a run is the Spring Batch job PRVDIR01, COMPLETED")
  void jobNameAndStatus() throws Exception {
    Map<String, Object> job = job(start("2026-03-31").path("runId").asText());
    assertThat(job.get("job_name")).isEqualTo("PRVDIR01");
    assertThat(job.get("status")).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-1] the job parameters are the run id and the business date 2026-03-31 (D-020)")
  void jobParameters() throws Exception {
    String runId = start("2026-03-31").path("runId").asText();
    Object execution = job(runId).get("id");
    assertThat(parameter(execution, "runId")).isEqualTo(runId);
    assertThat(parameter(execution, "businessDate")).isEqualTo("2026-03-31");
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-1] the job has the one step 'extract' whose write count is the number of records")
  void stepWriteCount() throws Exception {
    String runId = start("2026-03-31").path("runId").asText();
    List<Map<String, Object>> steps =
        jdbc.queryForList(
            "SELECT step_name, status, write_count FROM batch_step_execution WHERE job_execution_id = ?",
            job(runId).get("id"));
    assertThat(steps).hasSize(1);
    assertThat(steps.get(0))
        .containsEntry("step_name", "extract")
        .containsEntry("status", "COMPLETED");
    assertThat(((Number) steps.get(0).get("write_count")).intValue()).isEqualTo(2);
  }

  @Test
  @DisplayName("[MAM-67 AC-6] running again for the same date is a second job execution of its own")
  void rerunIsANewJob() throws Exception {
    String a = start("2026-03-31").path("runId").asText();
    String b = start("2026-03-31").path("runId").asText();
    assertThat(job(a).get("id")).isNotEqualTo(job(b).get("id"));
    assertThat(job(b).get("status")).isEqualTo("COMPLETED");
  }

  // ---- AC-7: a job that fails -----------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-67 AC-7] when the database rejects the write the 202 answer is a FAILED run with an error text and endedAt")
  void failedRunAnswer() throws Exception {
    rejectCompletedRuns();
    JsonNode run = start("2026-03-31");
    assertThat(run.path("status").asText()).isEqualTo("FAILED");
    assertThat(run.path("error").asText()).isNotBlank();
    assertThat(run.path("endedAt").asText()).isNotBlank();
    assertThat(run.path("job").asText()).isEqualTo("PRVDIR01");
    assertThat(run.path("businessDate").asText()).isEqualTo("2026-03-31");
  }

  @Test
  @DisplayName("[MAM-67 AC-7] the error text says what went wrong")
  void failedRunReason() throws Exception {
    rejectCompletedRuns();
    JsonNode run = start("2026-03-31");
    assertThat(run.path("error").asText()).contains("extract file rejected by the test database");
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-7] the error text of a failed run is a reason only: no SQL statement text (code review 2026-11-19)")
  void failedRunReasonHasNoSqlText() throws Exception {
    rejectCompletedRuns();
    JsonNode run = start("2026-03-31");
    assertThat(run.path("error").asText().toLowerCase())
        .doesNotContain("select", "insert", "update", "sql");
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-7] the error text of a failed run is short: at most 200 characters (code review 2026-11-19)")
  void failedRunReasonIsShort() throws Exception {
    rejectCompletedRuns();
    JsonNode run = start("2026-03-31");
    assertThat(run.path("error").asText()).isNotBlank().hasSizeLessThanOrEqualTo(200);
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-7] the reason stored with the failed run has no SQL statement text either (code review 2026-11-19)")
  void storedFailureReasonHasNoSqlText() throws Exception {
    rejectCompletedRuns();
    String runId = start("2026-03-31").path("runId").asText();
    String stored =
        jdbc.queryForObject(
            "SELECT failure_reason FROM extract_run WHERE public_id = ?::uuid",
            String.class,
            runId);
    assertThat(stored).isNotBlank();
    assertThat(stored.toLowerCase()).doesNotContain("select", "insert", "update", "sql");
  }

  @Test
  @DisplayName("[MAM-67 AC-7] the file of a FAILED run is 404, so there is no partial file")
  void failedRunHasNoFile() throws Exception {
    rejectCompletedRuns();
    String runId = start("2026-03-31").path("runId").asText();
    MvcResult r = mvc.perform(get(BASE + "/" + runId + "/file").with(PemJwt.admin())).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(404);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    String stored =
        jdbc.queryForObject(
            "SELECT extract_file FROM extract_run WHERE public_id = ?::uuid", String.class, runId);
    assertThat(stored).isNull();
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-7] the failed run stays visible: the status GET shows FAILED with the error")
  void failedRunReadAgain() throws Exception {
    rejectCompletedRuns();
    String runId = start("2026-03-31").path("runId").asText();
    MvcResult r = mvc.perform(get(BASE + "/" + runId).with(PemJwt.admin())).andReturn();
    JsonNode run = json.readTree(r.getResponse().getContentAsString());
    assertThat(run.path("status").asText()).isEqualTo("FAILED");
    assertThat(run.path("error").asText()).isNotBlank();
    assertThat(run.path("endedAt").asText()).isNotBlank();
  }

  @Test
  @DisplayName("[MAM-67 AC-7] the job execution of a failed run is FAILED as well")
  void failedJobExecution() throws Exception {
    rejectCompletedRuns();
    String runId = start("2026-03-31").path("runId").asText();
    assertThat(job(runId).get("status")).isEqualTo("FAILED");
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-7] after the failure the next run for the same date works and has its file")
  void nextRunAfterFailure() throws Exception {
    rejectCompletedRuns();
    start("2026-03-31");
    removeRejectingTrigger();
    JsonNode run = start("2026-03-31");
    assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
    MvcResult file =
        mvc.perform(get(BASE + "/" + run.path("runId").asText() + "/file").with(PemJwt.admin()))
            .andReturn();
    assertThat(file.getResponse().getStatus()).isEqualTo(200);
    assertThat(file.getResponse().getContentAsByteArray()).hasSize(200);
  }
}
