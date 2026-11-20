package gov.medicaid.pem.sanctions.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * sanctions_db.sanction_intake_run (MAM-55): each SANCIN intake run with its counts and failure
 * (D-024), and the sanctions it applied (sanction.intake_run_id).
 */
public interface SanctionIntakeRuns {

  /** One run (contract BatchRun); skipped = read - applied - notFound - rejected. */
  record Run(
      UUID runId,
      LocalDate businessDate,
      String status,
      int read,
      int applied,
      int notFound,
      int rejected,
      int terminated,
      String error,
      Instant startedAt,
      Instant endedAt) {

    public int skipped() {
      return Math.max(0, read - applied - notFound - rejected);
    }
  }

  void create(UUID runId, LocalDate businessDate, Instant startedAt);

  void counts(UUID runId, int read, int applied, int notFound, int rejected);

  void link(UUID sanctionId, UUID runId);

  /**
   * True when an earlier run for the same business date already applied this record (AC-7: a re-run
   * applies nothing twice).
   */
  boolean alreadyApplied(
      UUID runId, LocalDate businessDate, String npi, String action, String reason, LocalDate date);

  void complete(UUID runId, Instant endedAt);

  void fail(UUID runId, String error, Instant endedAt);

  Optional<Run> find(UUID runId);
}
