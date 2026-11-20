package gov.medicaid.pem.provider.application;

import gov.medicaid.pem.provider.domain.Provider;

/** Write port for new provider records (provider-service is the only writer, D-014). */
public interface ProviderRegistry {

  boolean exists(String npi);

  void add(Provider provider);
}
