package gov.medicaid.pem.provider.domain;

import java.util.Optional;

/**
 * A sanction status change (contract StatusChangeRequest.action; legacy SANCIN T, S, R): PRVSAN01
 * APPLY-SANCTION 106-112. SUSPEND sets the new SUSPENDED status instead of DENIED (D-006); every
 * action applies whatever the current status is (D-027, Q-006).
 */
public enum StatusAction {
  TERMINATE(EnrollStatus.TERMINATED),
  SUSPEND(EnrollStatus.SUSPENDED),
  REINSTATE(EnrollStatus.ACTIVE);

  private final EnrollStatus resultingStatus;

  StatusAction(EnrollStatus resultingStatus) {
    this.resultingStatus = resultingStatus;
  }

  public EnrollStatus resultingStatus() {
    return resultingStatus;
  }

  /** The provider with the resulting status; nothing else changes. */
  public Provider applyTo(Provider p) {
    return p.withEnrollStatus(resultingStatus);
  }

  /** An unknown or missing name gives empty (D-026: it is refused, never applied). */
  public static Optional<StatusAction> fromName(String name) {
    for (StatusAction a : values()) {
      if (a.name().equals(name)) {
        return Optional.of(a);
      }
    }
    return Optional.empty();
  }
}
