package gov.medicaid.pem.sanctions.application;

import gov.medicaid.pem.sanctions.domain.Sanction;

/** The sanction table (D-018): applied sanctions only, never changed or deleted. */
public interface SanctionHistory {

  void add(Sanction sanction);

  /** Newest first; npi null means every provider. */
  SanctionPage find(String npi, int page, int size);
}
