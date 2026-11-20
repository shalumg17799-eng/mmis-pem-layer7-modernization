package gov.medicaid.pem.screening.application;

/**
 * What screening needs to know about a provider, read from provider-service (Q-040 A): type,
 * taxonomy, the individual licence, the name as composed in D-028 (sent to SAM) and the owner NPI
 * of an organisation.
 */
public record ProviderFacts(
    String npi,
    String providerType,
    String taxonomy,
    String licenseNo,
    String licenseState,
    String name,
    String ownerNpi) {

  public boolean individual() {
    return "IN".equals(providerType);
  }
}
