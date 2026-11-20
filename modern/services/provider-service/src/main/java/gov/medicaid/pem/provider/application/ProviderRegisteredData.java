package gov.medicaid.pem.provider.application;

/** Data of the ProviderRegistered event (contracts/asyncapi/events.yaml#ProviderRegisteredData). */
public record ProviderRegisteredData(
    String npi,
    String medicaidId,
    String providerType,
    String taxonomy,
    String enrollStatus,
    String screenFlag,
    String displayName) {}
