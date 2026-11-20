package gov.medicaid.pem.tmsis.application;

import java.util.List;

/**
 * Port to provider-service, the one owner of provider data (D-014, ADR-002): one page of the ACTIVE
 * providers in ascending NPI order (GET /api/v1/providers?status=A, SYSTEM token).
 */
public interface ActiveProviders {

  /** At most 200 per page (the contract's limit). */
  int PAGE_SIZE = 200;

  record Page(List<ProviderRow> items, long total) {}

  Page page(int page, int size);
}
