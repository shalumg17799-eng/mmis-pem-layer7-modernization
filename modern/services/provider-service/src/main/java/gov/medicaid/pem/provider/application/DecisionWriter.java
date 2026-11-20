package gov.medicaid.pem.provider.application;

import gov.medicaid.pem.provider.domain.Provider;

/** Writes a decided provider (status and due date) back to its record; the version rises by one. */
public interface DecisionWriter {

  void save(Provider decided);
}
