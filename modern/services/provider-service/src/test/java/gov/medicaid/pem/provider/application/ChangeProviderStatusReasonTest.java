package gov.medicaid.pem.provider.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.medicaid.pem.kit.security.CurrentUser;
import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.provider.domain.EnrollStatus;
import gov.medicaid.pem.provider.domain.Provider;
import gov.medicaid.pem.provider.domain.ProviderType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MAM-33 AC-6: the reason of a status change is at most 60 characters (the contract and the history
 * entry say so); 60 is accepted and applied, 61 is a 422 that names the reason and changes and
 * announces nothing.
 */
class ChangeProviderStatusReasonTest {

  static final CurrentUser SYSTEM =
      new CurrentUser("service-account-pem-services", Set.of("SYSTEM"), Optional.empty());
  static final LocalDate EFFECTIVE = LocalDate.of(2026, 3, 31);

  final Provider active =
      Provider.builder()
          .npi("1003000111")
          .medicaidId("MCD000111")
          .providerType(ProviderType.INDIVIDUAL)
          .taxonomy("207Q00000X")
          .enrollStatus(EnrollStatus.ACTIVE)
          .effectiveDate(LocalDate.of(2026, 1, 15))
          .build();

  final List<Provider> saved = new ArrayList<>();
  final List<String> announced = new ArrayList<>();

  final ChangeProviderStatus change =
      new ChangeProviderStatus(
          npi -> Optional.of(active),
          saved::add,
          (type, subject, data) -> announced.add(type),
          () -> {});

  private static StatusChangeCommand suspend(String reason) {
    return new StatusChangeCommand("SUSPEND", reason, EFFECTIVE, UUID.randomUUID());
  }

  @Test
  @DisplayName(
      "[MAM-33 AC-6] a reason of exactly 60 characters is accepted: the status is saved and announced once")
  void sixtyCharactersAreAccepted() {
    Provider p = change.change("1003000111", suspend("R".repeat(60)), SYSTEM);

    assertThat(p.enrollStatus()).isEqualTo(EnrollStatus.SUSPENDED);
    assertThat(saved).containsExactly(p);
    assertThat(announced).containsExactly("ProviderStatusChanged");
  }

  @Test
  @DisplayName(
      "[MAM-33 AC-6] a reason of 61 characters is a 422 that names the reason, and nothing is saved or announced")
  void sixtyOneCharactersAreRefused() {
    assertThatThrownBy(() -> change.change("1003000111", suspend("R".repeat(61)), SYSTEM))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.status().value()).isEqualTo(422);
              assertThat(e.getMessage()).contains("reason").contains("60");
            });

    assertThat(saved).isEmpty();
    assertThat(announced).isEmpty();
  }

  @Test
  @DisplayName("[MAM-33 AC-6] no reason at all is accepted: the reason is optional")
  void noReasonIsAccepted() {
    Provider p = change.change("1003000111", suspend(null), SYSTEM);

    assertThat(p.enrollStatus()).isEqualTo(EnrollStatus.SUSPENDED);
    assertThat(announced).containsExactly("ProviderStatusChanged");
  }
}
