package gov.medicaid.pem.revalidation.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The sweep_run table: each run with its counts, failure reason and REVALQ file (ADR-004, D-024).
 */
public interface SweepRuns {

  void create(SweepRun run);

  void complete(UUID runId, int queued, int noDueDate, byte[] queueFile, Instant endedAt);

  void fail(UUID runId, String error, Instant endedAt);

  Optional<SweepRun> find(UUID runId);

  /** The REVALQ file of a completed run; empty when the run is unknown or has no file yet. */
  Optional<byte[]> queueFile(UUID runId);
}
