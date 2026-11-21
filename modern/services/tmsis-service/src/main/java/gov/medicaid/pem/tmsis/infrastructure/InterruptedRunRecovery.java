package gov.medicaid.pem.tmsis.infrastructure;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Puts right what a stop of tmsis-service during a run leaves behind (MAM-75 AC-6, D-024; code
 * review of tmsis-service 2026-11-20, MUST-FIX 2). A run is a thread of the service's JVM and only
 * that thread moves its run off RUNNING (the listener of the job, {@link TmsisExtractJobConfig}).
 * When the service stops during a run (a deploy, an out-of-memory kill, a restart) the thread dies
 * with it, so the run would stay RUNNING for ever and the failure would never be visible.
 *
 * <p>When the service has started, every run that is still RUNNING is therefore FAILED, with the
 * end time and the reason {@value #REASON}, and every job execution of PRVTMS01 that is still
 * running in the Spring Batch tables is ended as FAILED with its running steps. Nothing is archived
 * and no ExtractProduced event is published for such a run: the archive step never ran.
 *
 * <p>Assumption (ADR-004): one instance of tmsis-service per tmsis_db, as in docker compose. A run
 * or job execution that is running when this instance starts can only belong to an instance that
 * has stopped. Runs recorded while the service is up are never touched, because this runs once, at
 * the start.
 */
@Component
public class InterruptedRunRecovery {

  /** The reason shown for a run that was RUNNING when the service stopped. */
  static final String REASON = "interrupted: tmsis-service stopped during the run";

  private static final Logger LOG = LoggerFactory.getLogger(InterruptedRunRecovery.class);

  private final JdbcExtractRuns runs;
  private final JobExplorer explorer;
  private final JobRepository repository;
  private final Clock clock;

  public InterruptedRunRecovery(
      JdbcExtractRuns runs, JobExplorer explorer, JobRepository repository, Clock clock) {
    this.runs = runs;
    this.explorer = explorer;
    this.repository = repository;
    this.clock = clock;
  }

  /**
   * A problem here is logged and does not stop the service from starting: it is as well to serve
   * the other endpoints, and the log has the whole failure.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void failInterruptedRuns() {
    try {
      Instant now = clock.instant();
      for (UUID runId : runs.running()) {
        LOG.warn("PRVTMS01 run {} was RUNNING when tmsis-service started; now FAILED", runId);
        runs.fail(runId, REASON, now);
      }
      endRunningJobExecutions(now);
    } catch (RuntimeException e) {
      LOG.error("the runs that a stop left RUNNING could not all be put right", e);
    }
  }

  private void endRunningJobExecutions(Instant at) {
    LocalDateTime end = LocalDateTime.ofInstant(at, clock.getZone());
    ExitStatus exit = ExitStatus.FAILED.addExitDescription(REASON);
    for (JobExecution job : explorer.findRunningJobExecutions(TmsisExtractJobConfig.JOB)) {
      for (StepExecution step : job.getStepExecutions()) {
        if (step.getStatus().isRunning()) {
          step.setStatus(BatchStatus.FAILED);
          step.setExitStatus(exit);
          step.setEndTime(end);
          repository.update(step);
        }
      }
      job.setStatus(BatchStatus.FAILED);
      job.setExitStatus(exit);
      job.setEndTime(end);
      repository.update(job);
    }
  }
}
