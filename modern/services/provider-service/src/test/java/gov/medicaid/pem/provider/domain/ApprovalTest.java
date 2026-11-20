package gov.medicaid.pem.provider.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** MAM-27: the analyst decision (D-019) and the due date at approval (D-003). */
class ApprovalTest {

  static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

  static Provider provider(EnrollStatus status, ScreenFlag flag) {
    return new Provider(
        "1003000111",
        "MCD000111",
        ProviderType.INDIVIDUAL,
        "207Q00000X",
        null,
        status,
        flag,
        LocalDate.of(2026, 11, 1),
        null,
        null,
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
  @DisplayName(
      "[MAM-27 AC-1] APPROVE of a PENDING provider with a passed screening sets ACTIVE and due = date + 5 years")
  void approve() {
    Provider p =
        Approval.decide(provider(EnrollStatus.PENDING, ScreenFlag.PASS), Decision.APPROVE, TODAY);
    assertThat(p.enrollStatus()).isEqualTo(EnrollStatus.ACTIVE);
    assertThat(p.revalidationDueDate()).isEqualTo(LocalDate.of(2031, 11, 18));
    assertThat(p.screenFlag()).isEqualTo(ScreenFlag.PASS);
    assertThat(p.version()).isEqualTo(3);
    assertThat(p.npi()).isEqualTo("1003000111");
  }

  @Test
  @DisplayName(
      "[MAM-27 AC-1] the due date of an approval on 29 February falls on 28 February five years later")
  void leapDay() {
    Provider p =
        Approval.decide(
            provider(EnrollStatus.PENDING, ScreenFlag.PASS),
            Decision.APPROVE,
            LocalDate.of(2028, 2, 29));
    assertThat(p.revalidationDueDate()).isEqualTo(LocalDate.of(2033, 2, 28));
  }

  @Test
  @DisplayName(
      "[MAM-27 AC-3] DENY of a PENDING provider with a finished screening sets DENIED and no due date")
  void deny() {
    for (ScreenFlag flag : new ScreenFlag[] {ScreenFlag.FAIL, ScreenFlag.PASS}) {
      Provider p = Approval.decide(provider(EnrollStatus.PENDING, flag), Decision.DENY, TODAY);
      assertThat(p.enrollStatus()).isEqualTo(EnrollStatus.DENIED);
      assertThat(p.revalidationDueDate()).isNull();
    }
  }

  @Test
  @DisplayName(
      "[MAM-27 AC-4] APPROVE needs a passed screening: a failed or unscreened provider is refused (422)")
  void approveNeedsPass() {
    for (ScreenFlag flag : new ScreenFlag[] {ScreenFlag.FAIL, ScreenFlag.UNSCREENED}) {
      assertThatThrownBy(
              () -> Approval.decide(provider(EnrollStatus.PENDING, flag), Decision.APPROVE, TODAY))
          .isInstanceOfSatisfying(DecisionRefused.class, e -> assertThat(e.conflict()).isFalse());
    }
  }

  @Test
  @DisplayName(
      "[MAM-27 AC-4] DENY needs a finished screening: an unscreened provider is refused (422)")
  void denyNeedsFinished() {
    assertThatThrownBy(
            () ->
                Approval.decide(
                    provider(EnrollStatus.PENDING, ScreenFlag.UNSCREENED), Decision.DENY, TODAY))
        .isInstanceOfSatisfying(DecisionRefused.class, e -> assertThat(e.conflict()).isFalse());
  }

  @ParameterizedTest(name = "[MAM-27 AC-5] a provider that is {0} cannot be decided again (409)")
  @EnumSource(
      value = EnrollStatus.class,
      names = {"ACTIVE", "DENIED", "TERMINATED", "SUSPENDED"})
  void decidedOnce(EnrollStatus status) {
    for (Decision d : Decision.values()) {
      assertThatThrownBy(() -> Approval.decide(provider(status, ScreenFlag.PASS), d, TODAY))
          .isInstanceOfSatisfying(DecisionRefused.class, e -> assertThat(e.conflict()).isTrue());
    }
  }
}
