package gov.medicaid.pem.directory.application;

/** Port to provider-service GET /api/v1/providers/{npi} with the SYSTEM token. */
public interface ProviderFacts {

  ProviderDetails details(String npi);
}
