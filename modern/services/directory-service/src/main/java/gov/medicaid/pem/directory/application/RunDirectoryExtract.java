package gov.medicaid.pem.directory.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The directory extract (MAM-67; PRVDIR01): for an explicit business date (D-020) a RUNNING run is
 * recorded and its Spring Batch job runs to the end before the answer (AC-1: the 202 carries the
 * finished run). The job writes every ACTIVE entry of the read model (R-PRVDIR01-ADD-01) in
 * ascending NPI order as a 100-byte DIROUT record and completes the run with its row count; a run
 * that fails is FAILED with a short reason and has no file (AC-7, D-024); the full exception goes
 * to the log.
 */
@Service
public class RunDirectoryExtract {

  private static final Logger LOG = LoggerFactory.getLogger(RunDirectoryExtract.class);
  static final int PAGE = 200;

  /** The longest reason stored with a FAILED run. */
  private static final int REASON_MAX = 200;

  private final DirectoryExtracts runs;
  private final DirectoryStore store;
  private final DirectoryFile file;
  private final Clock clock;

  public RunDirectoryExtract(
      DirectoryExtracts runs, DirectoryStore store, DirectoryFile file, Clock clock) {
    this.runs = runs;
    this.store = store;
    this.file = file;
    this.clock = clock;
  }

  /** Records the RUNNING run; the job is then started by the caller. */
  public DirectoryExtracts.Run open(LocalDate businessDate) {
    DirectoryExtracts.Run run =
        new DirectoryExtracts.Run(
            UUID.randomUUID(),
            businessDate,
            DirectoryExtracts.Run.RUNNING,
            0,
            null,
            clock.instant(),
            null);
    runs.create(run);
    return run;
  }

  /**
   * The work of one run, called by the job's step inside its transaction; returns the rows. The
   * step transaction is REPEATABLE READ (DirectoryExtractJobConfig), so every page of the read
   * below sees the same snapshot and a concurrent change cannot duplicate or skip an entry between
   * two pages.
   */
  @Transactional
  public int execute(UUID runId, LocalDate businessDate) {
    List<DirectoryEntry> published = new ArrayList<>();
    for (int page = 0; ; page++) {
      DirectoryPage p = store.published(null, null, null, page, PAGE);
      published.addAll(p.items());
      if (p.items().size() < PAGE || (long) (page + 1) * PAGE >= p.total()) {
        break;
      }
    }
    runs.complete(runId, published.size(), file.write(published), clock.instant());
    LOG.info("PRVDIR01 run {} for {}: {} records written", runId, businessDate, published.size());
    return published.size();
  }

  /**
   * A run whose job did not complete (D-024). The stored reason is the first line of the root
   * cause's message, at most {@value #REASON_MAX} characters, so it holds no SQL text; the log has
   * the whole exception with its stack trace.
   */
  public void failed(UUID runId, LocalDate businessDate, Throwable cause) {
    LOG.error("PRVDIR01 run {} for {} FAILED", runId, businessDate, cause);
    runs.fail(runId, reasonOf(cause), clock.instant());
  }

  /** A run that failed for a reason that is already known (D-024). */
  public void failed(UUID runId, LocalDate businessDate, String reason) {
    LOG.error("PRVDIR01 run {} for {} FAILED: {}", runId, businessDate, reason);
    runs.fail(runId, reason, clock.instant());
  }

  private static String reasonOf(Throwable failure) {
    Throwable root = NestedExceptionUtils.getMostSpecificCause(failure);
    String message = root.getMessage() == null ? root.toString() : root.getMessage();
    String firstLine = message.strip().lines().findFirst().orElse(root.toString());
    return firstLine.substring(0, Math.min(firstLine.length(), REASON_MAX));
  }
}
