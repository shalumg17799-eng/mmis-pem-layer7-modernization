package gov.medicaid.pem.sanctions.application;

import java.time.LocalDate;

/**
 * The body of POST /api/v1/sanctions (contract SanctionRequest), as sent; checked by
 * RecordSanction.
 */
public record SanctionCommand(
    String npi, String action, String reason, LocalDate effectiveDate, String sourceFeed) {}
