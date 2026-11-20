package gov.medicaid.pem.revalidation.application;

import gov.medicaid.pem.revalidation.domain.CaseStatus;
import gov.medicaid.pem.revalidation.domain.RevalidationCase;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Reads and closes revalidation cases (MAM-48, MAM-49). */
public interface CaseCompletions {

  Optional<RevalidationCase> findCase(UUID caseId);

  /** The provider's case in DUE or IN_SCREENING, if any (at most one, D-029). */
  Optional<RevalidationCase> openCaseOf(String npi);

  void close(UUID caseId, CaseStatus status, UUID screeningId, Instant at);
}
