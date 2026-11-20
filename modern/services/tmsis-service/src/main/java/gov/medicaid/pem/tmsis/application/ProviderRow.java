package gov.medicaid.pem.tmsis.application;

import java.time.LocalDate;

/** The provider fields the extract needs, as provider-service answers them. */
public record ProviderRow(
    String npi, String medicaidId, String enrollStatus, LocalDate effectiveDate) {}
