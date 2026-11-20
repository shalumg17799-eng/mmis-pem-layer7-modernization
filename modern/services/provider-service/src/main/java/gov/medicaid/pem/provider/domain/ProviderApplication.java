package gov.medicaid.pem.provider.domain;

/**
 * The provider details of a validated application (ApplicationValidated data): identity, codes and
 * the PRV-VARIANT view that fits the type (individual for IN, organisation for GR and FA, both kept
 * as supplied; D-007, D-008).
 */
public record ProviderApplication(
    String npi,
    String medicaidId,
    ProviderType providerType,
    String taxonomy,
    String lastName,
    String firstName,
    String licenseNo,
    String licenseState,
    String legalName,
    String dbaName,
    String ownerNpi) {}
