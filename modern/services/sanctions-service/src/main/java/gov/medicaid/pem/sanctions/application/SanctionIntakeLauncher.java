package gov.medicaid.pem.sanctions.application;

import java.time.LocalDate;
import java.util.UUID;

/** Starts the Spring Batch job of one SANCIN intake run in the background (ADR-004). */
public interface SanctionIntakeLauncher {

  void launch(UUID runId, LocalDate businessDate, byte[] file, String recordedBy);
}
