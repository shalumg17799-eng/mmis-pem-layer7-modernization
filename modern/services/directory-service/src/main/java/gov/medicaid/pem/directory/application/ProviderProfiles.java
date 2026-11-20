package gov.medicaid.pem.directory.application;

/** Port to provider-service for the profile of a provider (GET /api/v1/providers/{npi}, SYSTEM). */
public interface ProviderProfiles {

  ProviderProfile profile(String npi);
}
