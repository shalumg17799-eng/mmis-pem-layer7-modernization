package gov.medicaid.pem.revalidation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * PRVREV01 CHECK-NEXT (lines 81-84) with the decisions D-003, D-006, D-020 and D-004 and question
 * C-11 (Q-049 answer A): an ACTIVE provider whose due date is on or before the business date is
 * queued; only ACTIVE providers are swept; the date compared is the explicit business date with a
 * 4-digit year; an ACTIVE provider without a due date is not queued but counted.
 */
class SweepRuleTest {

  static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 3, 31);

  static SweepDecision decide(String status, LocalDate due, LocalDate businessDate) {
    return SweepRule.decide(new TrackedProvider("1003000222", status, due), businessDate);
  }

  @ParameterizedTest(
      name =
          "[MAM-45 AC-2] an ACTIVE provider due on {0} is {1} on the business date 2026-03-31 (4-digit year dates)")
  @CsvSource({
    "2026-03-30, QUEUE", // due yesterday
    "2026-03-31, QUEUE", // due today: on or before the business date
    "2026-04-01, NOT_DUE", // due tomorrow
    "2026-02-28, QUEUE", // the example of AC-2
    "1999-12-31, QUEUE", // a date before 2000 is still a past date
    "1969-12-31, QUEUE", // legacy pivot 70: 69 meant 2069, and 70 meant 1970
    "2031-03-31, NOT_DUE", // five years on: what a completed revalidation sets
    "2069-12-31, NOT_DUE", // legacy pivot: YY 69 was read as 2069 and YY 70 as 1970
    "2070-01-01, NOT_DUE"
  })
  void activeProviderByDueDate(LocalDate due, SweepDecision expected) {
    assertThat(decide("A", due, BUSINESS_DATE)).isEqualTo(expected);
  }

  @ParameterizedTest(
      name =
          "[MAM-45 AC-1] the business date {0} decides, not the clock: a provider due 2026-03-31 is {1}")
  @CsvSource({
    "2026-03-30, NOT_DUE",
    "2026-03-31, QUEUE",
    "2026-04-01, QUEUE",
    "2026-04-30, QUEUE",
    "2031-03-31, QUEUE",
    "2020-01-01, NOT_DUE"
  })
  void theBusinessDateDecides(LocalDate businessDate, SweepDecision expected) {
    assertThat(decide("A", LocalDate.of(2026, 3, 31), businessDate)).isEqualTo(expected);
  }

  @ParameterizedTest(
      name = "[MAM-45 AC-2] leap day: a provider due 2028-02-29 is {1} on the business date {0}")
  @CsvSource({
    "2028-02-28, NOT_DUE",
    "2028-02-29, QUEUE",
    "2028-03-01, QUEUE",
  })
  void leapDay(LocalDate businessDate, SweepDecision expected) {
    assertThat(decide("A", LocalDate.of(2028, 2, 29), businessDate)).isEqualTo(expected);
  }

  @ParameterizedTest(
      name = "[MAM-45 AC-3] a provider with status {0} is not swept, however overdue it is (D-006)")
  @ValueSource(strings = {"P", "D", "T", "S"})
  void onlyActiveProvidersAreSwept(String status) {
    assertThat(decide(status, LocalDate.of(2026, 2, 28), BUSINESS_DATE))
        .isEqualTo(SweepDecision.NOT_ACTIVE);
  }

  @ParameterizedTest(
      name =
          "[MAM-45 AC-6] a provider with status {0} and no due date is NOT_ACTIVE, not NO_DUE_DATE")
  @ValueSource(strings = {"P", "D", "T", "S"})
  void notActiveWinsOverNoDueDate(String status) {
    assertThat(decide(status, null, BUSINESS_DATE)).isEqualTo(SweepDecision.NOT_ACTIVE);
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-6] an ACTIVE provider without a due date is not queued but counted as NO_DUE_DATE")
  void activeWithoutDueDate() {
    assertThat(decide("A", null, BUSINESS_DATE)).isEqualTo(SweepDecision.NO_DUE_DATE);
  }

  @Test
  @DisplayName("[MAM-45 AC-3] the status code is compared as given: a, blank and AA are not ACTIVE")
  void onlyTheCodeAIsActive() {
    for (String status : new String[] {"a", "", " ", "AA", "ACTIVE"}) {
      assertThat(decide(status, LocalDate.of(2026, 2, 28), BUSINESS_DATE))
          .as("status '%s'", status)
          .isEqualTo(SweepDecision.NOT_ACTIVE);
    }
  }

  @Test
  @DisplayName("[MAM-45 AC-2] the decision has the four values the sweep counts by")
  void fourDecisions() {
    assertThat(SweepDecision.values())
        .containsExactly(
            SweepDecision.QUEUE,
            SweepDecision.NOT_DUE,
            SweepDecision.NOT_ACTIVE,
            SweepDecision.NO_DUE_DATE);
  }
}
