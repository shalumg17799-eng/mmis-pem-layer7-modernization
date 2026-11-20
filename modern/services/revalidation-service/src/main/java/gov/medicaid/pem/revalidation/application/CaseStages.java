package gov.medicaid.pem.revalidation.application;

import java.util.UUID;

/** Moves a case between its open stages (MAM-49). */
public interface CaseStages {

  /** DUE becomes IN_SCREENING; false when the case is unknown or not DUE. */
  boolean markInScreening(UUID caseId);
}
