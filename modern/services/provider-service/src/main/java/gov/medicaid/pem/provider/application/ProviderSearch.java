package gov.medicaid.pem.provider.application;

import gov.medicaid.pem.provider.domain.EnrollStatus;
import gov.medicaid.pem.provider.domain.Provider;
import gov.medicaid.pem.provider.domain.ScreenFlag;
import java.util.List;

/**
 * Read port for searchProviders (staff only): every given filter must match; null means not
 * filtered, and no filter at all lists every provider (MAM-37 AC-6).
 */
public interface ProviderSearch {

  record Page(List<Provider> items, int page, int size, long total) {}

  /**
   * The filters of searchProviders: exact NPI, exact Medicaid ID (the legacy alternate index), name
   * contains (any of last, first, legal or DBA name, ignoring case), status and screen flag.
   */
  record Criteria(
      String npi, String medicaidId, String name, EnrollStatus status, ScreenFlag screenFlag) {}

  Page find(Criteria criteria, int page, int size);

  /**
   * Keyset paging (MAM-37 AC-9, Q-132, contract 1.2.0): up to size providers that match the
   * criteria and whose NPI is greater than afterNpi, in ascending NPI order. The key is only a
   * position: it need not be on file or match the criteria. The answer is page 0, and its total
   * counts every provider that matches the criteria, not only those after the key.
   */
  Page findAfter(Criteria criteria, String afterNpi, int size);
}
