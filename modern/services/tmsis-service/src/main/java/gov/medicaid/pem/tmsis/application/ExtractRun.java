package gov.medicaid.pem.tmsis.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One T-MSIS extract run (extract_run; contract BatchRun): RUNNING, then COMPLETED with its row
 * count and archive generation, or FAILED with the reason (D-024).
 */
public record ExtractRun(
    UUID runId,
    LocalDate businessDate,
    String periodYyddd,
    String status,
    int rows,
    int futureEnrolments,
    Integer archiveGeneration,
    String error,
    Instant startedAt,
    Instant endedAt) {

  public static final String RUNNING = "RUNNING";
  public static final String COMPLETED = "COMPLETED";
  public static final String FAILED = "FAILED";
}
