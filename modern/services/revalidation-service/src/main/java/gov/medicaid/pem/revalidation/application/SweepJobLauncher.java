package gov.medicaid.pem.revalidation.application;

import java.time.LocalDate;
import java.util.UUID;

/** Starts the Spring Batch job of one sweep run in the background (ADR-004, Q-102 A). */
public interface SweepJobLauncher {

  void launch(UUID runId, LocalDate businessDate);
}
