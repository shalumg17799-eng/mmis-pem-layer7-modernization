package gov.medicaid.pem.tmsis;

import gov.medicaid.pem.tmsis.application.ExtractRun;
import gov.medicaid.pem.tmsis.application.ExtractRuns;
import gov.medicaid.pem.tmsis.domain.ReportingPeriod;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A stop of tmsis-service during a run, and its next start (code review of tmsis-service
 * 2026-11-20, MUST-FIX 2; MAM-75 AC-6, D-024). A run is a thread of the service's JVM: when the
 * service stops during a run (a deploy, an out-of-memory kill, a restart), nothing in that JVM
 * marks the run FAILED. What a stop leaves behind is what {@link #crashedDuringRun} writes: a
 * RUNNING extract_run and, in the Spring Batch tables, a STARTED execution of the job PRVTMS01 for
 * that run (parameters runId and businessDate, as the launcher makes them) with its extract step
 * STARTED. The next start of the service is the moment to put that right, so the tests start it the
 * way Spring Boot does: it announces {@link ApplicationReadyEvent} (the reviewer's fix is a
 * listener of that event, and one instance per tmsis_db is assumed, as in docker compose).
 */
public final class ServiceRestart {

  /** The job name of the extract (the legacy program id). */
  public static final String JOB = "PRVTMS01";

  /** When the crashed runs began: before the test clock (2026-01-15, 10:00 UTC). */
  public static final Instant STARTED_AT = Instant.parse("2026-01-15T09:00:00Z");

  private ServiceRestart() {}

  /**
   * The state a stop leaves behind for a run of this business date: a RUNNING run and, when {@code
   * withJobExecution}, its STARTED job execution and step. Without a job execution the stop came
   * between the recording of the run and the start of its job. Returns the run id.
   */
  public static UUID crashedDuringRun(
      JdbcTemplate jdbc, ExtractRuns runs, String businessDate, boolean withJobExecution) {
    UUID runId = UUID.randomUUID();
    LocalDate date = LocalDate.parse(businessDate);
    runs.create(
        new ExtractRun(
            runId,
            date,
            ReportingPeriod.yyddd(date),
            ExtractRun.RUNNING,
            0,
            0,
            null,
            null,
            STARTED_AT,
            null));
    if (withJobExecution) {
      startedJobExecution(jdbc, runId, date);
    }
    return runId;
  }

  private static void startedJobExecution(JdbcTemplate jdbc, UUID runId, LocalDate date) {
    Timestamp at = Timestamp.valueOf(LocalDateTime.of(2026, 1, 15, 9, 0));
    long instance = jdbc.queryForObject("SELECT nextval('batch_job_seq')", Long.class);
    long execution = jdbc.queryForObject("SELECT nextval('batch_job_execution_seq')", Long.class);
    long step = jdbc.queryForObject("SELECT nextval('batch_step_execution_seq')", Long.class);
    jdbc.update(
        "INSERT INTO batch_job_instance (job_instance_id, version, job_name, job_key)"
            + " VALUES (?, 0, ?, md5(?))",
        instance,
        JOB,
        runId.toString());
    jdbc.update(
        "INSERT INTO batch_job_execution (job_execution_id, version, job_instance_id, create_time,"
            + " start_time, status, exit_code, last_updated) VALUES (?, 1, ?, ?, ?, 'STARTED',"
            + " 'UNKNOWN', ?)",
        execution,
        instance,
        at,
        at,
        at);
    jdbc.update(
        "INSERT INTO batch_job_execution_params (job_execution_id, parameter_name, parameter_type,"
            + " parameter_value, identifying) VALUES (?, 'runId', 'java.lang.String', ?, 'Y')",
        execution,
        runId.toString());
    jdbc.update(
        "INSERT INTO batch_job_execution_params (job_execution_id, parameter_name, parameter_type,"
            + " parameter_value, identifying) VALUES (?, 'businessDate', 'java.time.LocalDate', ?,"
            + " 'Y')",
        execution,
        date.toString());
    jdbc.update(
        "INSERT INTO batch_step_execution (step_execution_id, version, step_name, job_execution_id,"
            + " create_time, start_time, status, commit_count, read_count, filter_count,"
            + " write_count, read_skip_count, write_skip_count, process_skip_count,"
            + " rollback_count, exit_code, last_updated) VALUES (?, 1, 'extract', ?, ?, ?,"
            + " 'STARTED', 1, 200, 0, 200, 0, 0, 0, 0, 'EXECUTING', ?)",
        step,
        execution,
        at,
        at,
        at);
  }

  /** The status of the job execution of this run, null when the run has none. */
  public static String jobStatus(JdbcTemplate jdbc, UUID runId) {
    return jdbc
        .query(
            "SELECT e.status FROM batch_job_execution e JOIN batch_job_execution_params p ON"
                + " p.job_execution_id = e.job_execution_id WHERE p.parameter_name = 'runId' AND"
                + " p.parameter_value = ?",
            (rs, n) -> rs.getString(1),
            runId.toString())
        .stream()
        .findFirst()
        .orElse(null);
  }

  /** True when the job execution of this run has an end time. */
  public static boolean jobEnded(JdbcTemplate jdbc, UUID runId) {
    Integer ended =
        jdbc.queryForObject(
            "SELECT count(*) FROM batch_job_execution e JOIN batch_job_execution_params p ON"
                + " p.job_execution_id = e.job_execution_id WHERE p.parameter_name = 'runId' AND"
                + " p.parameter_value = ? AND e.end_time IS NOT NULL",
            Integer.class,
            runId.toString());
    return ended != null && ended > 0;
  }

  /**
   * tmsis-service starts again: Spring Boot has finished starting the application and announces
   * ApplicationReadyEvent to the listeners of the context.
   */
  public static void startsAgain(ConfigurableApplicationContext context) {
    context.publishEvent(
        new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO));
  }

  /**
   * After a test: what the test left RUNNING or STARTED (a recovery that does not exist) is ended,
   * so that it cannot hold the next test. Called after the assertions, never before them.
   */
  public static void endWhatIsLeft(JdbcTemplate jdbc) {
    jdbc.update(
        "UPDATE extract_run SET status = 'FAILED', failure_reason = 'left RUNNING by the test',"
            + " ended_at = now() WHERE status = 'RUNNING'");
    jdbc.update(
        "UPDATE batch_job_execution SET status = 'FAILED', end_time = now() WHERE status IN"
            + " ('STARTING', 'STARTED')");
    jdbc.update(
        "UPDATE batch_step_execution SET status = 'FAILED', end_time = now() WHERE status IN"
            + " ('STARTING', 'STARTED')");
  }
}
