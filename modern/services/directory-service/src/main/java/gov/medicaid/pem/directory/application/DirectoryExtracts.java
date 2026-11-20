package gov.medicaid.pem.directory.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** directory_db.extract_run: each directory extract run with its count, file or failure (D-024). */
public interface DirectoryExtracts {

  /** One run (contract BatchRun). */
  record Run(
      UUID runId,
      LocalDate businessDate,
      String status,
      int rows,
      String error,
      Instant startedAt,
      Instant endedAt) {

    public static final String RUNNING = "RUNNING";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";
  }

  void create(Run run);

  void complete(UUID runId, int rows, byte[] file, Instant endedAt);

  void fail(UUID runId, String error, Instant endedAt);

  Optional<Run> find(UUID runId);

  /** The DIROUT file of a COMPLETED run. */
  Optional<byte[]> file(UUID runId);
}
