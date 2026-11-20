package gov.medicaid.pem.revalidation.domain;

import java.time.LocalDate;

/**
 * A provider as revalidation-service knows it: its status and due date from the latest
 * ProviderStatusChanged (read model; provider-service owns the record, D-014).
 */
public record TrackedProvider(String npi, String enrollStatus, LocalDate dueDate) {}
