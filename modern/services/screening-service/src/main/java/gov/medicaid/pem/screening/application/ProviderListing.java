package gov.medicaid.pem.screening.application;

import java.util.List;

/**
 * Port to provider-service GET /api/v1/providers (every provider, page by page, SYSTEM token): the
 * batch screening reads the whole master as PRVSCR01 did (MAM-32 AC-2).
 */
public interface ProviderListing {

  int PAGE_SIZE = 200;

  record Row(String npi, String screenFlag) {}

  record Page(List<Row> items, long total) {}

  Page page(int page, int size);
}
