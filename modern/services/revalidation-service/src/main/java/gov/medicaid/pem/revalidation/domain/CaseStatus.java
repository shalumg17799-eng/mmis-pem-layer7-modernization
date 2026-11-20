package gov.medicaid.pem.revalidation.domain;

/** The state of a revalidation case (D-029); DUE and IN_SCREENING are open. */
public enum CaseStatus {
  DUE,
  IN_SCREENING,
  COMPLETED,
  CANCELLED;

  public boolean open() {
    return this == DUE || this == IN_SCREENING;
  }
}
