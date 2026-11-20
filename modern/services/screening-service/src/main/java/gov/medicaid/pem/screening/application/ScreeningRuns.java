package gov.medicaid.pem.screening.application;

import gov.medicaid.pem.screening.domain.SourceResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * screening_db.screening_run (MAM-32): each batch screening run with its counts, SCRNOUT file or
 * failure (D-024), and the screenings it started (screening.run_id).
 */
public interface ScreeningRuns {

  /** One run (contract BatchRun; counts read, screened, skipped, failed). */
  record Run(
      UUID runId,
      LocalDate businessDate,
      String status,
      int read,
      int screened,
      int failed,
      String error,
      Instant startedAt,
      Instant endedAt) {

    public static final String RUNNING = "RUNNING";

    public int skipped() {
      return Math.max(0, read - screened);
    }
  }

  /** One screened provider of a run with its four source results, for the SCRNOUT file. */
  record Screened(String npi, List<SourceResult> results) {}

  void create(UUID runId, LocalDate businessDate, Instant startedAt);

  /** Links a screening to the run that started it. */
  void link(UUID screeningId, UUID runId);

  /** True when the NPI already has an INITIAL screening (RUNNING or COMPLETED), AC-7. */
  boolean hasInitialScreening(String npi);

  /** The run's screenings in ascending NPI order with their results. */
  List<Screened> screened(UUID runId);

  void complete(UUID runId, int read, int failed, byte[] file, Instant endedAt);

  void fail(UUID runId, int read, String error, Instant endedAt);

  Optional<Run> find(UUID runId);

  Optional<byte[]> file(UUID runId);
}
