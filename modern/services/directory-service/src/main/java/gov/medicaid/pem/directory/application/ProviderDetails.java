package gov.medicaid.pem.directory.application;

/** The provider fields the directory needs, read from provider-service (Q-071 default A). */
public record ProviderDetails(
    String npi,
    String medicaidId,
    String providerType,
    String taxonomy,
    String firstName,
    String lastName,
    String legalName) {}
