package gov.medicaid.pem.provider.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** MAM-33: PRVSAN01 APPLY-SANCTION (106-112) with the SUSPENDED fix (D-006) and D-027. */
class StatusActionTest {

  static Provider provider(EnrollStatus status) {
    return new Provider(
        "1003000111",
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
        3);
  }

  @Test
  @DisplayName("[MAM-33 AC-1] TERMINATE gives TERMINATED")
  void terminate() {
    assertThat(StatusAction.TERMINATE.applyTo(provider(EnrollStatus.ACTIVE)).enrollStatus())
        .isEqualTo(EnrollStatus.TERMINATED);
  }

  @Test
  @DisplayName("[MAM-33 AC-2] SUSPEND gives SUSPENDED, not DENIED")
  void suspend() {
    assertThat(StatusAction.SUSPEND.applyTo(provider(EnrollStatus.ACTIVE)).enrollStatus())
        .isEqualTo(EnrollStatus.SUSPENDED)
        .isNotEqualTo(EnrollStatus.DENIED);
  }

  @Test
  @DisplayName("[MAM-33 AC-3] REINSTATE gives ACTIVE")
  void reinstate() {
    assertThat(StatusAction.REINSTATE.applyTo(provider(EnrollStatus.SUSPENDED)).enrollStatus())
        .isEqualTo(EnrollStatus.ACTIVE);
  }

  @ParameterizedTest(name = "from {0}")
  @EnumSource(EnrollStatus.class)
  @DisplayName("[MAM-33 AC-4] every action applies whatever the current status")
  void anyCurrentStatus(EnrollStatus current) {
    assertThat(StatusAction.TERMINATE.applyTo(provider(current)).enrollStatus())
        .isEqualTo(EnrollStatus.TERMINATED);
    assertThat(StatusAction.SUSPEND.applyTo(provider(current)).enrollStatus())
        .isEqualTo(EnrollStatus.SUSPENDED);
    assertThat(StatusAction.REINSTATE.applyTo(provider(current)).enrollStatus())
        .isEqualTo(EnrollStatus.ACTIVE);
  }

  @Test
  @DisplayName("[MAM-33 AC-5] only the status changes; dates, codes, names and version are kept")
  void onlyTheStatus() {
    Provider before = provider(EnrollStatus.ACTIVE);
    Provider after = StatusAction.SUSPEND.applyTo(before);
    assertThat(after).usingRecursiveComparison().ignoringFields("enrollStatus").isEqualTo(before);
  }

  @Test
  @DisplayName("[MAM-33 AC-6] only TERMINATE, SUSPEND and REINSTATE are known actions")
  void names() {
    assertThat(StatusAction.fromName("TERMINATE")).contains(StatusAction.TERMINATE);
    assertThat(StatusAction.fromName("SUSPEND")).contains(StatusAction.SUSPEND);
    assertThat(StatusAction.fromName("REINSTATE")).contains(StatusAction.REINSTATE);
    assertThat(StatusAction.fromName("PURGE")).isEmpty();
    assertThat(StatusAction.fromName("T")).isEmpty();
    assertThat(StatusAction.fromName("")).isEmpty();
    assertThat(StatusAction.fromName(null)).isEmpty();
  }
}
