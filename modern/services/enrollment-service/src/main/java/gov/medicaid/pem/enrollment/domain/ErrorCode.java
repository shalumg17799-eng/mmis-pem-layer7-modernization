package gov.medicaid.pem.enrollment.domain;

/**
 * The enrolment validation codes of ERRCODES.cpy, always with their full four characters (D-016).
 */
public enum ErrorCode {
  /** Missing NPI (EC-MISSING-NPI); also an NPI that is not 10 digits (R-PRVENR01-ADD-05). */
  E101("Enter the 10-digit NPI."),
  /** Invalid provider type: not IN, GR or FA and not a mapped feed code (D-008). */
  E102("The provider type must be IN, GR or FA."),
  /** Duplicate NPI: an application for this NPI is already VALIDATED (R-PRVENR01-ADD-04). */
  E103("An application for this NPI has already been accepted."),
  /** Missing taxonomy. */
  E104("Enter the taxonomy code.");

  private final String message;

  ErrorCode(String message) {
    this.message = message;
  }

  public String message() {
    return message;
  }
}
