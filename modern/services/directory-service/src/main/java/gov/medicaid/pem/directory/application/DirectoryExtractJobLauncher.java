package gov.medicaid.pem.directory.application;

import java.time.LocalDate;
import java.util.UUID;

/** Runs the Spring Batch job of one directory extract and returns when it has ended (ADR-004). */
public interface DirectoryExtractJobLauncher {

  void run(UUID runId, LocalDate businessDate);
}
