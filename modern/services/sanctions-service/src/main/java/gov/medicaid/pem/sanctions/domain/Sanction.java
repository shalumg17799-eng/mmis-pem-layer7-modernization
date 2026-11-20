package gov.medicaid.pem.sanctions.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One applied sanction of the history (D-018: the reason and effective date PRVSAN01 never kept,
 * who recorded it and the resulting status).
 */
public record Sanction(
    UUID sanctionId,
    String npi,
    SanctionAction action,
    String reason,
    LocalDate effectiveDate,
    String sourceFeed,
    String recordedBy,
    Instant recordedAt) {

  public String resultingStatus() {
    return action.resultingStatus();
  }
}
