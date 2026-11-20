package gov.medicaid.pem.provider.application;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The body of POST /api/v1/providers/{npi}/status-changes (contract StatusChangeRequest), as sent:
 * the action is checked by {@link ChangeProviderStatus}.
 */
public record StatusChangeCommand(
    String action, String reason, LocalDate effectiveDate, UUID sanctionId) {}
