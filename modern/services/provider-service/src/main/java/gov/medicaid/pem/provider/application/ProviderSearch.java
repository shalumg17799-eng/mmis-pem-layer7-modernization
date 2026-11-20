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
}
