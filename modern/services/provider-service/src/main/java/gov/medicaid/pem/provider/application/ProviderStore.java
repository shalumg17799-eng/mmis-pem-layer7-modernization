package gov.medicaid.pem.provider.application;

import gov.medicaid.pem.provider.domain.Provider;
import java.util.Optional;

/** Port to the provider records (implemented in infrastructure with JPA). */
public interface ProviderStore {

  Optional<Provider> findByNpi(String npi);
}
