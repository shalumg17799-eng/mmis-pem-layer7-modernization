package gov.medicaid.pem.provider.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MAM-17: a validated application becomes a PENDING, UNSCREENED provider (PRVENR01 write path,
 * D-001, D-003).
 */
class ProviderRegistrationTest {

  static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

  static ProviderApplication rivera() {
    return new ProviderApplication(
        "1003000111",
        "MCD000111",
        ProviderType.INDIVIDUAL,
        "207Q00000X",
        "RIVERA",
        "MARIA",
        "MD12345",
        "TX",
        null,
        null,
        null);
  }

  static ProviderApplication clinic() {
    return new ProviderApplication(
        "1003000444",
        "MCD000444",
        ProviderType.GROUP,
        "251E00000X",
        null,
        null,
        null,
        null,
        "EXCLUDED CLINIC LLC",
        "EXCLUDED CLINIC",
        "1990000111");
  }

  @Test
  @DisplayName(
      "[MAM-17 AC-1] the service sets PENDING and UNSCREENED, no tier and no due date; the fields are kept as supplied")
  void individual() {
    Provider p = ProviderRegistration.register(rivera(), TODAY);
    assertThat(p.enrollStatus()).isEqualTo(EnrollStatus.PENDING);
    assertThat(p.screenFlag()).isEqualTo(ScreenFlag.UNSCREENED);
    assertThat(p.riskTier()).isNull();
    assertThat(p.revalidationDueDate()).isNull();
    assertThat(p.endDate()).isNull();
    assertThat(p.npi()).isEqualTo("1003000111");
    assertThat(p.medicaidId()).isEqualTo("MCD000111");
    assertThat(p.providerType()).isEqualTo(ProviderType.INDIVIDUAL);
    assertThat(p.taxonomy()).isEqualTo("207Q00000X");
    assertThat(p.lastName()).isEqualTo("RIVERA");
    assertThat(p.firstName()).isEqualTo("MARIA");
    assertThat(p.licenseNo()).isEqualTo("MD12345");
    assertThat(p.licenseState()).isEqualTo("TX");
    assertThat(p.legalName()).isNull();
    assertThat(p.version()).isZero();
  }

  @Test
  @DisplayName("[MAM-17 AC-7] the effective date is the registration date")
  void effectiveDate() {
    assertThat(ProviderRegistration.register(rivera(), TODAY).effectiveDate()).isEqualTo(TODAY);
  }

  @Test
  @DisplayName("[MAM-17 AC-3] a group keeps its legal name, DBA name and owner NPI")
  void group() {
    Provider p = ProviderRegistration.register(clinic(), TODAY);
    assertThat(p.providerType()).isEqualTo(ProviderType.GROUP);
    assertThat(p.legalName()).isEqualTo("EXCLUDED CLINIC LLC");
    assertThat(p.dbaName()).isEqualTo("EXCLUDED CLINIC");
    assertThat(p.ownerNpi()).isEqualTo("1990000111");
    assertThat(p.lastName()).isNull();
    assertThat(p.enrollStatus()).isEqualTo(EnrollStatus.PENDING);
  }

  @Test
  @DisplayName("[MAM-17 AC-2] the display name of an individual is first name + last name (D-028)")
  void displayIndividual() {
    assertThat(ProviderRegistration.displayName(ProviderRegistration.register(rivera(), TODAY)))
        .isEqualTo("MARIA RIVERA");
  }

  @Test
  @DisplayName("[MAM-17 AC-3] the display name of a group or facility is its legal name (D-028)")
  void displayOrganisation() {
    assertThat(ProviderRegistration.displayName(ProviderRegistration.register(clinic(), TODAY)))
        .isEqualTo("EXCLUDED CLINIC LLC");
  }
}
