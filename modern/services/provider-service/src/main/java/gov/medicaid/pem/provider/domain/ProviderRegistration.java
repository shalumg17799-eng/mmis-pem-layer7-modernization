package gov.medicaid.pem.provider.domain;

import java.time.LocalDate;

/**
 * PRVENR01 write path (146-148: SET PRV-PENDING, SET PRV-UNSCREENED, WRITE): a validated
 * application becomes a PENDING, UNSCREENED provider. The status is always set here, never taken
 * from the input (D-001); there is no risk tier and no revalidation due date before approval
 * (D-003, D-019); the effective date is the registration date (Q-A1).
 */
public final class ProviderRegistration {

  private ProviderRegistration() {}

  public static Provider register(ProviderApplication a, LocalDate registrationDate) {
    return new Provider(
        a.npi(),
        a.medicaidId(),
        a.providerType(),
        a.taxonomy(),
        null,
        EnrollStatus.PENDING,
        ScreenFlag.UNSCREENED,
        registrationDate,
        null,
        null,
        a.lastName(),
        a.firstName(),
        a.licenseNo(),
        a.licenseState(),
        a.legalName(),
        a.dbaName(),
        a.ownerNpi(),
        0);
  }

  /**
   * D-028: an individual is shown as first name + last name; a group or facility by its legal name.
   */
  public static String displayName(Provider p) {
    if (p.providerType() == ProviderType.INDIVIDUAL) {
      return (nz(p.firstName()) + " " + nz(p.lastName())).trim();
    }
    return nz(p.legalName()).trim();
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }
}
