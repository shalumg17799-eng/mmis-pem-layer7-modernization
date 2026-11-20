package gov.medicaid.pem.directory.application;

import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * startDirectoryExtract (MAM-67): records the run, runs its job to the end (ADR-004) and answers
 * with the finished run, COMPLETED or FAILED (AC-1, AC-7). A job that cannot even be started is
 * recorded as FAILED with the reason.
 */
@Service
public class StartDirectoryExtract {

  private final RunDirectoryExtract extract;
  private final DirectoryExtractJobLauncher launcher;
  private final DirectoryExtracts runs;

  public StartDirectoryExtract(
      RunDirectoryExtract extract, DirectoryExtractJobLauncher launcher, DirectoryExtracts runs) {
    this.extract = extract;
    this.launcher = launcher;
    this.runs = runs;
  }

  public DirectoryExtracts.Run start(LocalDate businessDate) {
    DirectoryExtracts.Run run = extract.open(businessDate);
    try {
      launcher.run(run.runId(), businessDate);
    } catch (RuntimeException e) {
      extract.failed(run.runId(), businessDate, e);
    }
    return runs.find(run.runId()).orElse(run);
  }
}
