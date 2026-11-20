package gov.medicaid.pem.provider.application;

import java.time.LocalDate;

/**
 * Data of the ProviderStatusChanged event
 * (contracts/asyncapi/events.yaml#ProviderStatusChangedData).
 */
public record ProviderStatusChangedData(
    String npi,
    String from,
    String to,
    String reason,
    String actor,
    LocalDate revalidationDueDate,
    String providerType,
    String displayName,
    String taxonomy,
    String medicaidId) {}
