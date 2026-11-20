package gov.medicaid.pem.revalidation.application;

import gov.medicaid.pem.revalidation.domain.CaseStatus;
import gov.medicaid.pem.revalidation.domain.RevalidationCase;
import gov.medicaid.pem.revalidation.domain.TrackedProvider;
import java.util.List;
import java.util.UUID;

/** The tracked providers and the revalidation cases of revalidation_db. */
public interface CaseStore {

  List<TrackedProvider> trackedProviders();

  boolean hasOpenCase(String npi);

  void open(RevalidationCase opened, UUID runId);

  /** Oldest due date first; null filters mean not filtered. */
  CasePage find(String npi, CaseStatus status, int page, int size);
}
