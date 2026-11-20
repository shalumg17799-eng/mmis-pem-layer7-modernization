package gov.medicaid.pem.directory.application;

/**
 * One provider in the directory read model (contract DirectoryEntry plus the enrolment status that
 * decides publication).
 */
public record DirectoryEntry(
    String npi,
    String medicaidId,
    String providerType,
    String riskTier,
    String enrollStatus,
    String displayName,
    String taxonomy) {}
