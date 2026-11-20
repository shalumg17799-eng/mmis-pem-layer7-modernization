package gov.medicaid.pem.tmsis.application;

import gov.medicaid.pem.tmsis.domain.ReportingPeriod;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * startTmsisExtract (MAM-71, MAM-75; PRVMTH01 STEP020-030): records a RUNNING run for the explicit
 * business date (D-020) and starts its job in the background; the answer is the RUNNING run. A run
 * that cannot be started is recorded as FAILED with the reason (D-024).
 */
@Service
public class StartTmsisExtract {

  private static final Logger LOG = LoggerFactory.getLogger(StartTmsisExtract.class);

  private final ExtractRuns runs;
  private final ExtractJobLauncher launcher;
  private final Clock clock;

  public StartTmsisExtract(ExtractRuns runs, ExtractJobLauncher launcher, Clock clock) {
    this.runs = runs;
    this.launcher = launcher;
    this.clock = clock;
  }

  public ExtractRun start(LocalDate businessDate) {
    ExtractRun run =
        new ExtractRun(
            UUID.randomUUID(),
            businessDate,
            ReportingPeriod.yyddd(businessDate),
            ExtractRun.RUNNING,
            0,
            0,
            null,
            null,
            clock.instant(),
            null);
    runs.create(run);
    try {
      launcher.launch(run.runId(), businessDate);
    } catch (RuntimeException e) {
      LOG.error("PRVTMS01 run {} for {} could not be started: {}", run.runId(), businessDate, e.toString());
      runs.fail(run.runId(), "could not be started: " + e.getMessage(), clock.instant());
    }
    return runs.find(run.runId()).orElse(run);
  }

  /** ADR-004: the scheduled run takes the business date from the injected Clock. */
  public ExtractRun startForToday() {
    return start(LocalDate.now(clock));
  }
}
