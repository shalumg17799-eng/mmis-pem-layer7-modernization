package gov.medicaid.pem.provider.application;

import gov.medicaid.pem.kit.security.CurrentUser;
import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.provider.domain.Provider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads one provider by NPI (replaces the PINQ READ of PRVPROVN, D-022). Access is checked first,
 * so a PROVIDER cannot learn whether another NPI exists (D-023).
 */
@Service
public class FindProvider {

  private final ProviderStore store;

  public FindProvider(ProviderStore store) {
    this.store = store;
  }

  @Transactional(readOnly = true)
  public Provider byNpi(String npi, CurrentUser user) {
    user.requireAccessToNpi(npi);
    return store
        .findByNpi(npi)
        .orElseThrow(() -> ApiException.notFound("No provider found for NPI " + npi));
  }
}
