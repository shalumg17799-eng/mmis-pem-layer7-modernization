package gov.medicaid.pem.revalidation.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * startRevalidationSweep (MAM-45, PRVREV01 and PRVMTH01 STEP010): records a RUNNING run for an
 * explicit business date (D-020) and starts its Spring Batch job in the background (ADR-004, Q-102
 * A); the job's step does the work (ExecuteSweep). A run that cannot be started is recorded FAILED
 * with a short reason without SQL text (FailureReason); the log has the whole exception (D-024).
 */
@Service
public class RunSweep {

  private static final Logger LOG = LoggerFactory.getLogger(RunSweep.class);

  private final SweepRuns runs;
  private final SweepJobLauncher launcher;
  private final Clock clock;

  public RunSweep(SweepRuns runs, SweepJobLauncher launcher, Clock clock) {
    this.runs = runs;
    this.launcher = launcher;
    this.clock = clock;
  }

  /** Records a RUNNING run and starts its job in the background; the answer is the RUNNING run. */
  public SweepRun start(LocalDate businessDate) {
    SweepRun run =
        new SweepRun(
            UUID.randomUUID(), businessDate, SweepRun.RUNNING, 0, 0, null, clock.instant(), null);
    runs.create(run);
    try {
      launcher.launch(run.runId(), businessDate);
    } catch (RuntimeException e) {
      String reason = FailureReason.of(e);
      LOG.error("Revalidation sweep {} for {} FAILED: {}", run.runId(), businessDate, reason, e);
      runs.fail(run.runId(), reason, clock.instant());
    }
    return run;
  }
}
