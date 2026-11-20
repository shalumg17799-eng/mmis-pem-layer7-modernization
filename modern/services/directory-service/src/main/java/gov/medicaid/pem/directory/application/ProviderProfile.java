package gov.medicaid.pem.directory.application;

/**
 * The legacy facts a FHIR resource may carry beyond the directory entry (MAM-65, Q-008, ADR-006):
 * the individual's names and licence, or the organisation's legal name. Nothing else (no address,
 * telecom or network) exists in the legacy master.
 */
public record ProviderProfile(
    String npi,
    String firstName,
    String lastName,
    String legalName,
    String licenseNo,
    String licenseState) {}
