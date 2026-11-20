package gov.medicaid.pem.sanctions.application;

import java.time.LocalDate;
import java.util.UUID;

/**
 * provider-service's status-change API (POST /api/v1/providers/{npi}/status-changes), the only way
 * a sanction reaches the provider (D-013).
 */
public interface ProviderStatusPort {

  /** APPLIED: the status was changed; NOT_FOUND: no such provider; FAILED: anything else. */
  enum Outcome {
    APPLIED,
    NOT_FOUND,
    FAILED
  }

  Outcome change(
      String npi, String action, String reason, LocalDate effectiveDate, UUID sanctionId);
}
