package gov.medicaid.pem.sanctions.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Data of the SanctionApplied event (contracts/asyncapi/events.yaml#SanctionAppliedData). The
 * contract's reason is a string that cannot be null, so a sanction without a reason has no reason
 * member.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SanctionAppliedData(
    UUID sanctionId, String npi, String action, String reason, LocalDate effectiveDate) {}
