package gov.medicaid.pem.screening.application;

import java.time.LocalDate;
import java.util.UUID;

/** Starts the Spring Batch job of one batch screening run in the background (ADR-004). */
public interface ScreeningBatchLauncher {

  void launch(UUID runId, LocalDate businessDate);
}
