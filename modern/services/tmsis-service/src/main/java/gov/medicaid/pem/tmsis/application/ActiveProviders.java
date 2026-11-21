package gov.medicaid.pem.tmsis.application;

import java.util.List;

/**
 * Port to provider-service, the one owner of provider data (D-014, ADR-002): the ACTIVE providers
 * in ascending NPI order (GET /api/v1/providers?status=A, SYSTEM token).
 */
public interface ActiveProviders {

  /** At most 200 per page (the contract's limit). */
  int PAGE_SIZE = 200;

  /** One answer: its providers, and how many ACTIVE providers there are in all at that moment. */
  record Page(List<ProviderRow> items, long total) {}

  /** One page by offset, the form of contract 1.1.0 (the extract walks by key: {@link #after}). */
  Page page(int page, int size);

  /**
   * The next at most {@code size} providers whose NPI is greater than {@code afterNpi}, in
   * ascending NPI order: the keyset form of contract 1.2.0 (Q-132). A null key is the start of a
   * walk; each next request has the NPI of the last item of the answer before; an empty list means
   * nothing is left. The total counts all ACTIVE providers, not only those after the key.
   */
  Page after(String afterNpi, int size);
}
