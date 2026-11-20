package gov.medicaid.pem.revalidation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.revalidation.IntegrationTest;
import gov.medicaid.pem.revalidation.RevalidationFixtures;
import gov.medicaid.pem.revalidation.application.SweepJobLauncher;
import gov.medicaid.pem.testsupport.PemJwt;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * The sweep is a Spring Batch job (ADR-004, question Q-102 answer A): job "revalidation-sweep" with
 * the job parameters runId and businessDate (D-020), one step "sweep" that reads every tracked
 * provider, writes the cases it opens and filters the rest; Spring Batch keeps the job and step
 * records in revalidation_db (Flyway V3). Runs never overlap (one thread), and a job that cannot
 * be started is a failed run, not a lost one (D-024).
 */
@Timeout(240)
class SweepJobTest extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired SweepJobLauncher launcher;

  @BeforeEach
  void cleanBefore() {
    RevalidationFixtures.reset(jdbc);
  }

  @AfterEach
  void cleanAfter() {
    RevalidationFixtures.reset(jdbc);
  }

  private Map<String, Object> step(UUID run) {
    return jdbc.queryForMap(
        "SELECT s.step_name, s.status, s.read_count, s.write_count, s.filter_count"
            + " FROM batch_step_execution s"
            + " JOIN batch_job_execution_params p ON p.job_execution_id = s.job_execution_id"
            + " WHERE p.parameter_name = 'runId' AND p.parameter_value = ?",
        run.toString());
  }

  private String jobStatus(UUID run) {
    return jdbc.queryForObject(
        "SELECT e.status FROM batch_job_execution e"
            + " JOIN batch_job_execution_params p ON p.job_execution_id = e.job_execution_id"
            + " WHERE p.parameter_name = 'runId' AND p.parameter_value = ?",
        String.class,
        run.toString());
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-8] a run is a COMPLETED Spring Batch job revalidation-sweep with one step sweep")
  void runIsASpringBatchJob() throws Exception {
    RevalidationFixtures.track(jdbc, "1003000222", "A", LocalDate.of(2026, 2, 28));

    UUID run = RevalidationFixtures.sweep(mvc, "2026-03-31");

    assertThat(jobStatus(run)).isEqualTo("COMPLETED");
    assertThat(
            jdbc.queryForObject(
                "SELECT j.job_name FROM batch_job_instance j"
                    + " JOIN batch_job_execution e ON e.job_instance_id = j.job_instance_id"
                    + " JOIN batch_job_execution_params p ON p.job_execution_id = e.job_execution_id"
                    + " WHERE p.parameter_name = 'runId' AND p.parameter_value = ?",
                String.class,
                run.toString()))
        .isEqualTo("revalidation-sweep");
    assertThat(step(run)).containsEntry("step_name", "sweep").containsEntry("status", "COMPLETED");
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-1] the job parameters are the run id and the explicit business date (D-020)")
  void jobParameters() throws Exception {
    UUID run = RevalidationFixtures.sweep(mvc, "2031-03-31");

    List<Map<String, Object>> params =
        jdbc.queryForList(
            "SELECT p.parameter_name, p.parameter_value FROM batch_job_execution_params p"
                + " WHERE p.job_execution_id = (SELECT p2.job_execution_id FROM batch_job_execution_params p2"
                + " WHERE p2.parameter_name = 'runId' AND p2.parameter_value = ?)",
            run.toString());
    assertThat(params)
        .extracting(m -> m.get("parameter_name") + "=" + m.get("parameter_value"))
        .containsExactlyInAnyOrder("runId=" + run, "businessDate=2031-03-31");
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-8] the step reads every tracked provider, writes the cases it opens and filters the rest")
  void stepCounts() throws Exception {
    RevalidationFixtures.track(jdbc, "1003000222", "A", LocalDate.of(2026, 2, 28)); // queued
    RevalidationFixtures.track(jdbc, "1003000333", "A", LocalDate.of(2026, 3, 31)); // queued
    RevalidationFixtures.track(jdbc, "1003000444", "A", LocalDate.of(2026, 4, 1)); // not due
    RevalidationFixtures.track(jdbc, "1003000555", "S", LocalDate.of(2026, 2, 28)); // suspended
    RevalidationFixtures.track(jdbc, "1003000666", "A", null); // no due date

    UUID run = RevalidationFixtures.sweep(mvc, "2026-03-31");

    Map<String, Object> step = step(run);
    assertThat(((Number) step.get("read_count")).intValue()).isEqualTo(5);
    assertThat(((Number) step.get("write_count")).intValue()).isEqualTo(2);
    assertThat(((Number) step.get("filter_count")).intValue()).isEqualTo(3);
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-8] a sweep over no provider reads, writes and filters nothing and still completes")
  void emptyStep() throws Exception {
    UUID run = RevalidationFixtures.sweep(mvc, "2026-03-31");

    Map<String, Object> step = step(run);
    assertThat(((Number) step.get("read_count")).intValue()).isZero();
    assertThat(((Number) step.get("write_count")).intValue()).isZero();
    assertThat(((Number) step.get("filter_count")).intValue()).isZero();
    assertThat(jobStatus(run)).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-5] each run is its own job instance, also for the same business date twice")
  void everyRunIsItsOwnJobInstance() throws Exception {
    UUID first = RevalidationFixtures.sweep(mvc, "2026-03-31");
    UUID second = RevalidationFixtures.sweep(mvc, "2026-03-31");

    assertThat(first).isNotEqualTo(second);
    assertThat(jobStatus(first)).isEqualTo("COMPLETED");
    assertThat(jobStatus(second)).isEqualTo("COMPLETED");
    assertThat(
            RevalidationFixtures.count(
                jdbc,
                "SELECT count(*) FROM batch_job_instance WHERE job_name = 'revalidation-sweep'"))
        .isEqualTo(2);
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-5] two runs started one after the other never overlap: the first opens the case, the second finds it open")
  void runsDoNotOverlap() throws Exception {
    RevalidationFixtures.track(jdbc, "1003000222", "A", LocalDate.of(2026, 2, 28));

    UUID first = RevalidationFixtures.startAsAdmin(mvc, "2026-03-31");
    UUID second = RevalidationFixtures.startAsAdmin(mvc, "2026-03-31");
    JsonNode one = RevalidationFixtures.awaitRun(mvc, first);
    JsonNode two = RevalidationFixtures.awaitRun(mvc, second);

    assertThat(one.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(two.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(one.path("counts").path("queued").asInt()).isEqualTo(1);
    assertThat(two.path("counts").path("queued").asInt()).isZero();
    assertThat(RevalidationFixtures.casesOf(jdbc, "1003000222")).isEqualTo(1);
    assertThat(RevalidationFixtures.outbox(jdbc, "RevalidationDue")).hasSize(1);
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-8] a job that cannot be started (its run id was used) is refused with the job's name in the message")
  void aJobThatCannotBeStartedIsRefused() throws Exception {
    UUID run = UUID.randomUUID();
    launcher.launch(run, LocalDate.of(2026, 3, 31));
    Awaitility.await("the job of run " + run + " ends")
        .atMost(Duration.ofSeconds(45))
        .pollInterval(Duration.ofMillis(200))
        .until(
            () ->
                RevalidationFixtures.count(
                        jdbc,
                        "SELECT count(*) FROM batch_job_execution e"
                            + " JOIN batch_job_execution_params p ON p.job_execution_id = e.job_execution_id"
                            + " WHERE p.parameter_name = 'runId' AND p.parameter_value = ?"
                            + " AND e.status = 'COMPLETED'",
                        run.toString())
                    == 1);

    assertThatThrownBy(() -> launcher.launch(run, LocalDate.of(2026, 3, 31)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("revalidation-sweep")
        .hasMessageContaining("not started");
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-8] the manual trigger answers at once and the run is still RUNNING or already ended, never lost")
  void theRunIsRecordedBeforeTheJobEnds() throws Exception {
    UUID run = RevalidationFixtures.startAsAdmin(mvc, "2026-03-31");

    String status =
        jdbc.queryForObject(
            "SELECT status FROM sweep_run WHERE public_id = ?", String.class, run);
    assertThat(status).isIn("RUNNING", "COMPLETED");
    RevalidationFixtures.awaitRun(mvc, run);
    assertThat(
            mvc.perform(
                    MockMvcRequestBuilders.get(RevalidationFixtures.SWEEPS + "/{runId}", run)
                        .with(PemJwt.admin()))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(200);
  }
}
