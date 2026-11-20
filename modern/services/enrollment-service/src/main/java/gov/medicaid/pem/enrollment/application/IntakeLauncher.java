package gov.medicaid.pem.enrollment.application;

import java.time.LocalDate;
import java.util.UUID;

/** Starts the Spring Batch job of one APPINPUT intake run in the background (ADR-004). */
public interface IntakeLauncher {

  void launch(UUID runId, LocalDate businessDate, byte[] file, String submittedBy);
}
