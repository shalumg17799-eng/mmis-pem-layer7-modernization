package gov.medicaid.pem.provider.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.medicaid.pem.kit.security.CurrentUser;
import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.provider.domain.EnrollStatus;
import gov.medicaid.pem.provider.domain.Provider;
import gov.medicaid.pem.provider.domain.ProviderType;
import gov.medicaid.pem.provider.domain.RiskTier;
import gov.medicaid.pem.provider.domain.ScreenFlag;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MAM-33: the status-change use case saves the new status, announces ProviderStatusChanged once,
 * counts terminations, and refuses explicitly (D-024, D-026).
 */
class ChangeProviderStatusTest {

  static final CurrentUser SYSTEM =
      new CurrentUser("service-account-pem-services", Set.of("SYSTEM"), Optional.empty());
  static final LocalDate EFFECTIVE = LocalDate.of(2026, 3, 31);

  record Sent(String type, String subject, Object data) {}

  final Map<String, Provider> db = new HashMap<>();
  final List<Provider> saved = new ArrayList<>();
  final List<Sent> sent = new ArrayList<>();
  final AtomicInteger terminations = new AtomicInteger();

  final ChangeProviderStatus change =
      new ChangeProviderStatus(
          npi -> Optional.ofNullable(db.get(npi)),
          saved::add,
          (type, subject, data) -> sent.add(new Sent(type, subject, data)),
          terminations::incrementAndGet);

  static Provider provider(String npi, EnrollStatus status) {
    return new Provider(
        npi,
        "MCD000111",
        ProviderType.INDIVIDUAL,
        "207Q00000X",
        RiskTier.LIMITED,
        status,
        ScreenFlag.PASS,
        LocalDate.of(2026, 1, 15),
        null,
        LocalDate.of(2031, 1, 15),
        "RIVERA",
        "MARIA",
        "MD12345",
        "TX",
        null,
        null,
        null,
        1);
  }

  static StatusChangeCommand command(String action, String reason, LocalDate effective) {
    return new StatusChangeCommand(action, reason, effective, UUID.randomUUID());
  }

  @Test
  @DisplayName("[MAM-33 AC-1] TERMINATE sets T, is saved and counted once")
  void terminate() {
    db.put("1003000111", provider("1003000111", EnrollStatus.ACTIVE));
    Provider p =
        change.change("1003000111", command("TERMINATE", "LEIE exclusion", EFFECTIVE), SYSTEM);
    assertThat(p.enrollStatus()).isEqualTo(EnrollStatus.TERMINATED);
    assertThat(saved).containsExactly(p);
    assertThat(terminations.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("[MAM-33 AC-2] SUSPEND sets S, not D, and is not counted as a termination")
  void suspend() {
    db.put("1003000111", provider("1003000111", EnrollStatus.ACTIVE));
    Provider p =
        change.change("1003000111", command("SUSPEND", "LEIE exclusion", EFFECTIVE), SYSTEM);
    assertThat(p.enrollStatus()).isEqualTo(EnrollStatus.SUSPENDED);
    assertThat(terminations.get()).isZero();
  }

  @Test
  @DisplayName("[MAM-33 AC-3] REINSTATE sets A and is not counted as a termination")
  void reinstate() {
    db.put("1003000111", provider("1003000111", EnrollStatus.SUSPENDED));
    Provider p =
        change.change("1003000111", command("REINSTATE", null, LocalDate.of(2026, 4, 30)), SYSTEM);
    assertThat(p.enrollStatus()).isEqualTo(EnrollStatus.ACTIVE);
    assertThat(terminations.get()).isZero();
  }

  @Test
  @DisplayName("[MAM-33 AC-4] REINSTATE of a DENIED and TERMINATE of a PENDING provider apply")
  void anyStatus() {
    db.put("1003000444", provider("1003000444", EnrollStatus.DENIED));
    db.put("1003000222", provider("1003000222", EnrollStatus.PENDING));
    assertThat(
            change
                .change("1003000444", command("REINSTATE", null, LocalDate.of(2026, 4, 30)), SYSTEM)
                .enrollStatus())
        .isEqualTo(EnrollStatus.ACTIVE);
    assertThat(
            change
                .change("1003000222", command("TERMINATE", null, EFFECTIVE), SYSTEM)
                .enrollStatus())
        .isEqualTo(EnrollStatus.TERMINATED);
  }

  @Test
  @DisplayName(
      "[MAM-33 AC-5] the change is announced once with from, to, reason, actor and the provider facts")
  void announced() {
    db.put("1003000111", provider("1003000111", EnrollStatus.ACTIVE));
    change.change("1003000111", command("SUSPEND", "LEIE exclusion", EFFECTIVE), SYSTEM);
    assertThat(sent)
        .containsExactly(
            new Sent(
                "ProviderStatusChanged",
                "1003000111",
                new ProviderStatusChangedData(
                    "1003000111",
                    "A",
                    "S",
                    "LEIE exclusion",
                    "service-account-pem-services",
                    LocalDate.of(2031, 1, 15),
                    "IN",
                    "MARIA RIVERA",
                    "207Q00000X",
                    "MCD000111")));
  }

  @Test
  @DisplayName(
      "[MAM-33 AC-6] an unknown action is a 422 and nothing is saved, announced or counted")
  void unknownAction() {
    db.put("1003000111", provider("1003000111", EnrollStatus.ACTIVE));
    assertThatThrownBy(() -> change.change("1003000111", command("PURGE", null, EFFECTIVE), SYSTEM))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.status().value()).isEqualTo(422);
              assertThat(e.getMessage()).contains("action");
              assertThat(e.code()).isNull();
            });
    assertThat(saved).isEmpty();
    assertThat(sent).isEmpty();
    assertThat(terminations.get()).isZero();
  }

  @Test
  @DisplayName("[MAM-33 AC-6] a missing effective date is a 422 and nothing is saved or announced")
  void missingDate() {
    db.put("1003000111", provider("1003000111", EnrollStatus.ACTIVE));
    assertThatThrownBy(() -> change.change("1003000111", command("TERMINATE", null, null), SYSTEM))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.status().value()).isEqualTo(422);
              assertThat(e.getMessage()).contains("effectiveDate");
            });
    assertThat(saved).isEmpty();
    assertThat(sent).isEmpty();
    assertThat(terminations.get()).isZero();
  }

  @Test
  @DisplayName("[MAM-33 AC-7] an unknown provider is a 404 and nothing is saved or announced")
  void unknownProvider() {
    assertThatThrownBy(
            () -> change.change("1003000999", command("TERMINATE", null, EFFECTIVE), SYSTEM))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.status().value()).isEqualTo(404);
              assertThat(e.getMessage()).contains("1003000999");
            });
    assertThat(saved).isEmpty();
    assertThat(sent).isEmpty();
    assertThat(terminations.get()).isZero();
  }
}
