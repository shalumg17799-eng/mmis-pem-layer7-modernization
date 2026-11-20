package gov.medicaid.pem.tmsis.application;

import java.time.LocalDate;
import java.util.UUID;

/** Starts the Spring Batch job of one run in the background (ADR-004). */
public interface ExtractJobLauncher {

  void launch(UUID runId, LocalDate businessDate);
}
