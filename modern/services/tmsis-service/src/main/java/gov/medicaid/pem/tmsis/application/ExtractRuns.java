package gov.medicaid.pem.tmsis.application;

import gov.medicaid.pem.tmsis.domain.TmsisRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The runs, their rows and the archive generations on tmsis_db. */
public interface ExtractRuns {

  record RunPage(List<ExtractRun> items, long total) {}

  void create(ExtractRun run);

  void addRows(UUID runId, List<TmsisRecord> rows);

  /** MAM-78: adds flagged future enrolments to the run's count. */
  void addFutureEnrolments(UUID runId, int count);

  /** The run's rows in ascending NPI order (the key order of the legacy master). */
  List<TmsisRecord> rows(UUID runId);

  /**
   * Archives the file as the next generation (GDG MMPRV.TMSIS.ARCHIVE(+1)) and marks the run
   * COMPLETED with its row count; returns the generation.
   */
  int complete(UUID runId, int rows, byte[] file, Instant at);

  void fail(UUID runId, String error, Instant at);

  Optional<ExtractRun> find(UUID runId);

  /** Newest first. */
  RunPage page(int page, int size);

  /** The archived file of a COMPLETED run. */
  Optional<byte[]> file(UUID runId);
}
