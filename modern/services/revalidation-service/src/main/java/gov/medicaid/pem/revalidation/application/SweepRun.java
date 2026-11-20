package gov.medicaid.pem.revalidation.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One sweep run (contract BatchRun): RUNNING, then COMPLETED with its counts or FAILED with why.
 */
public record SweepRun(
    UUID runId,
    LocalDate businessDate,
    String status,
    int queued,
    int noDueDate,
    String error,
    Instant startedAt,
    Instant endedAt) {

  public static final String RUNNING = "RUNNING";
}
