package gov.medicaid.pem.revalidation.domain;

/** What the sweep does with one tracked provider. */
public enum SweepDecision {
  QUEUE,
  NOT_DUE,
  NOT_ACTIVE,
  NO_DUE_DATE
}
