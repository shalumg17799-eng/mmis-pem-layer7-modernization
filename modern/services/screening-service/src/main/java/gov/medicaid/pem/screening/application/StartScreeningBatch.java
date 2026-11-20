package gov.medicaid.pem.screening.application;

import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * startScreeningRun (MAM-32 AC-1): records a RUNNING run for the explicit business date (D-020) and
 * starts its job in the background; the answer is the RUNNING run. A job that cannot be started is
 * recorded FAILED with the reason.
 */
@Service
public class StartScreeningBatch {

  private final RunScreeningBatch batch;
  private final ScreeningBatchLauncher launcher;
  private final ScreeningRuns runs;

  public StartScreeningBatch(
      RunScreeningBatch batch, ScreeningBatchLauncher launcher, ScreeningRuns runs) {
    this.batch = batch;
    this.launcher = launcher;
    this.runs = runs;
  }

  public ScreeningRuns.Run start(LocalDate businessDate) {
    ScreeningRuns.Run run = batch.open(businessDate);
    try {
      launcher.launch(run.runId(), businessDate);
    } catch (RuntimeException e) {
      batch.failed(run.runId(), businessDate, 0, e);
      return runs.find(run.runId()).orElse(run);
    }
    return run;
  }
}
