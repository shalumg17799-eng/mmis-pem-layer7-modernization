package gov.medicaid.pem.provider.domain;

import java.time.LocalDate;

/**
 * The analyst decision (D-019, Q-A2): only a PENDING enrolment can be decided; APPROVE needs a
 * passed screening and sets ACTIVE with the revalidation due date = approval date + 5 years
 * (D-003); DENY needs a finished screening (P or F) and sets DENIED without a due date.
 */
public final class Approval {

  static final int REVALIDATION_YEARS = 5;

  private Approval() {}

  public static Provider decide(Provider p, Decision decision, LocalDate today) {
    if (p.enrollStatus() != EnrollStatus.PENDING) {
      throw new DecisionRefused(
          true, "The enrolment of " + p.npi() + " was already decided (" + p.enrollStatus() + ").");
    }
    if (decision == Decision.APPROVE) {
      if (p.screenFlag() != ScreenFlag.PASS) {
        throw new DecisionRefused(false, "Approval needs a passed screening.");
      }
      return with(p, EnrollStatus.ACTIVE, today.plusYears(REVALIDATION_YEARS));
    }
    if (p.screenFlag() == ScreenFlag.UNSCREENED) {
      throw new DecisionRefused(false, "Denial needs a finished screening.");
    }
    return with(p, EnrollStatus.DENIED, null);
  }

  private static Provider with(Provider p, EnrollStatus status, LocalDate due) {
    return new Provider(
        p.npi(),
        p.medicaidId(),
        p.providerType(),
        p.taxonomy(),
        p.riskTier(),
        status,
        p.screenFlag(),
        p.effectiveDate(),
        p.endDate(),
        due,
        p.lastName(),
        p.firstName(),
        p.licenseNo(),
        p.licenseState(),
        p.legalName(),
        p.dbaName(),
        p.ownerNpi(),
        p.version());
  }
}
