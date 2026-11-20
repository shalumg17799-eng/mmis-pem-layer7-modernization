package gov.medicaid.pem.provider.domain;

/**
 * Enrolment status (PRV-ENROLL-STATUS). S, SUSPENDED, is new (D-006). The stored value is the
 * legacy code.
 */
public enum EnrollStatus {
  PENDING("P"),
  ACTIVE("A"),
  TERMINATED("T"),
  DENIED("D"),
  SUSPENDED("S");

  private final String code;

  EnrollStatus(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  public static EnrollStatus fromCode(String code) {
    for (EnrollStatus v : values()) {
      if (v.code.equals(code == null ? null : code.trim())) {
        return v;
      }
    }
    throw new IllegalArgumentException("unknown EnrollStatus code: " + code);
  }
}
