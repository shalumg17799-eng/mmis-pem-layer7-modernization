package gov.medicaid.pem.revalidation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** MAM-45: PRVREV01 CHECK-NEXT (81-84) with the decisions D-003, D-006, D-020 and Q-049 (C-11). */
class SweepRuleTest {

  static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 3, 31);

  static SweepDecision decide(String status, LocalDate due) {
    return SweepRule.decide(new TrackedProvider("1003000222", status, due), BUSINESS_DATE);
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-2] an ACTIVE provider due before or on the business date is queued (4-digit-year dates)")
  void due() {
    assertThat(decide("A", LocalDate.of(2026, 2, 28))).isEqualTo(SweepDecision.QUEUE);
    assertThat(decide("A", LocalDate.of(2026, 3, 31))).isEqualTo(SweepDecision.QUEUE);
    assertThat(decide("A", LocalDate.of(1999, 12, 31))).isEqualTo(SweepDecision.QUEUE);
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-3] a provider due after the business date is not queued, whatever today's date is")
  void notDue() {
    assertThat(decide("A", LocalDate.of(2026, 4, 1))).isEqualTo(SweepDecision.NOT_DUE);
    assertThat(decide("A", LocalDate.of(2031, 3, 31))).isEqualTo(SweepDecision.NOT_DUE);
  }

  @Test
  @DisplayName("[MAM-45 AC-3] only ACTIVE providers are swept: S, T, D and P are not queued")
  void notActive() {
    for (String status : new String[] {"S", "T", "D", "P"}) {
      assertThat(decide(status, LocalDate.of(2026, 2, 28))).isEqualTo(SweepDecision.NOT_ACTIVE);
    }
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-6] an ACTIVE provider without a due date is not queued but counted as no due date")
  void noDueDate() {
    assertThat(decide("A", null)).isEqualTo(SweepDecision.NO_DUE_DATE);
  }
}
