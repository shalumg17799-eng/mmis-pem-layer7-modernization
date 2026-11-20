package gov.medicaid.pem.provider.application;

import gov.medicaid.pem.kit.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * getProviderAudit (MAM-40): the history of one provider, oldest first and paged. An NPI that is
 * not on file is a 404, never an empty history.
 */
@Service
public class ReadProviderHistory {

  private final ProviderStore store;
  private final AuditHistory history;

  public ReadProviderHistory(ProviderStore store, AuditHistory history) {
    this.store = store;
    this.history = history;
  }

  @Transactional(readOnly = true)
  public AuditHistory.Page of(String npi, int page, int size) {
    if (store.findByNpi(npi).isEmpty()) {
      throw ApiException.notFound("No provider found for NPI " + npi);
    }
    return history.of(npi, page, size);
  }
}
