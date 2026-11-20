package gov.medicaid.pem.enrollment.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * enrollment_db.intake_run (MAM-56, MAM-57): each APPINPUT intake run with its batch id, counts,
 * APPERROR rows and failure (D-024); applications of a run carry intake_run_id and raw_record.
 */
public interface IntakeRuns {

  /** One run (contract BatchRun; counts read, written, errored, skipped). */
  record Run(
      UUID runId,
      LocalDate businessDate,
      String batchId,
      String status,
      int read,
      int written,
      int errored,
      int skipped,
      String error,
      Instant startedAt,
      Instant endedAt) {}

  void create(UUID runId, LocalDate businessDate, String batchId, Instant startedAt);

  void counts(UUID runId, int read, int written, int errored, int skipped);

  /** Adds one 250-byte APPERROR row to the run's error file. */
  void addErrorRow(UUID runId, String row);

  /** Ties an application to the run and keeps its 200-byte input record. */
  void attach(UUID applicationId, UUID runId, String rawRecord);

  /** True when another run for the same business date already processed this record (AC-8). */
  boolean alreadyProcessed(UUID runId, LocalDate businessDate, String rawRecord);

  void complete(UUID runId, Instant endedAt);

  void fail(UUID runId, String error, Instant endedAt);

  Optional<Run> find(UUID runId);

  /** The APPERROR rows written so far (empty when none). */
  Optional<String> errorFile(UUID runId);
}
