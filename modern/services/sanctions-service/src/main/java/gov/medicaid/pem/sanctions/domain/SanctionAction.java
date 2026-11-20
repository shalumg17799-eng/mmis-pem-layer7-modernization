package gov.medicaid.pem.sanctions.domain;

import java.util.Optional;

/**
 * The SANCIN action codes (PRVSAN01 SAN-TERMINATE, SAN-SUSPEND, SAN-REINSTATE; APPLY-SANCTION
 * 106-112): the provider-service status change each one asks for and the status it results in. S
 * results in SUSPENDED, no longer DENIED (D-006); any other code is unknown and rejected (D-026).
 */
public enum SanctionAction {
  T("TERMINATE", "T"),
  S("SUSPEND", "S"),
  R("REINSTATE", "A");

  private final String statusChange;
  private final String resultingStatus;

  SanctionAction(String statusChange, String resultingStatus) {
    this.statusChange = statusChange;
    this.resultingStatus = resultingStatus;
  }

  /** The action of provider-service's StatusChangeRequest. */
  public String statusChange() {
    return statusChange;
  }

  /** The provider's enrolment status after the sanction (contract Sanction.resultingStatus). */
  public String resultingStatus() {
    return resultingStatus;
  }

  public static Optional<SanctionAction> fromCode(String code) {
    for (SanctionAction a : values()) {
      if (a.name().equals(code)) {
        return Optional.of(a);
      }
    }
    return Optional.empty();
  }
}
