package gov.medicaid.pem.tmsis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * MAM-78: an ACTIVE provider whose enrolment YEAR is strictly later than the year of the business
 * date is flagged (R-PRVTMS01-cbl-00005, -00035, D-004, D-020, Q-062 default B). Only the year
 * counts, and four-digit years are compared, so the rule is right for every century.
 */
class FutureEnrolmentTest {

  static LocalDate d(String iso) {
    return LocalDate.parse(iso);
  }

  static Stream<Arguments> acCases() {
    return Stream.of(
        Arguments.of("[MAM-78 AC-1]", "2027-01-15", "2026-03-31", true),
        Arguments.of("[MAM-78 AC-3]", "2026-03-31", "2026-03-31", false),
        Arguments.of("[MAM-78 AC-3]", "2026-12-31", "2026-03-31", false),
        Arguments.of("[MAM-78 AC-3]", "2027-01-01", "2026-03-31", true),
        Arguments.of("[MAM-78 AC-4]", "1998-05-01", "2026-03-31", false),
        Arguments.of("[MAM-78 AC-5]", "2101-01-01", "2026-03-31", true));
  }

  @ParameterizedTest(name = "{0} effective {1} against business date {2}: flagged = {3}")
  @MethodSource("acCases")
  void acceptanceCases(String ac, String effective, String business, boolean flagged) {
    assertThat(FutureEnrolment.flagged(d(effective), d(business))).isEqualTo(flagged);
  }

  static Stream<Arguments> boundaries() {
    return Stream.of(
        Arguments.of("the last day of the year before", "2025-12-31", "2026-01-01", false),
        Arguments.of("the first day of the same year", "2026-01-01", "2026-12-31", false),
        Arguments.of("the first day of the next year", "2027-01-01", "2026-12-31", true),
        Arguments.of("the last day of the next year", "2027-12-31", "2026-01-01", true),
        Arguments.of("a year in the 1900s", "1999-12-31", "2026-03-31", false),
        Arguments.of("the year 2000", "2000-01-01", "2026-03-31", false),
        Arguments.of("the year 2099", "2099-06-01", "2026-03-31", true),
        Arguments.of("the year 2100", "2100-01-01", "2026-03-31", true),
        Arguments.of(
            "an enrolment year 2099 in a business year 2100", "2099-12-31", "2100-01-01", false),
        Arguments.of("the same far year", "2101-05-05", "2101-01-01", false),
        Arguments.of("the next far year", "2102-01-01", "2101-12-31", true),
        Arguments.of("a business date in a leap year", "2029-01-01", "2028-02-29", true));
  }

  @ParameterizedTest(name = "[MAM-78 AC-3] {0}: effective {1}, business date {2}: flagged = {3}")
  @MethodSource("boundaries")
  void yearBoundaries(String what, String effective, String business, boolean flagged) {
    assertThat(FutureEnrolment.flagged(d(effective), d(business))).isEqualTo(flagged);
  }

  @Test
  @DisplayName(
      "[MAM-78 AC-3] only the year counts: a later month or day of the same year is not flagged (OQ-D9)")
  void laterInTheSameYearIsNotFlagged() {
    assertThat(FutureEnrolment.flagged(d("2026-12-31"), d("2026-01-01"))).isFalse();
    assertThat(FutureEnrolment.flagged(d("2026-04-01"), d("2026-03-31"))).isFalse();
  }

  @Test
  @DisplayName(
      "[MAM-78 AC-4] the legacy two-digit test flagged every pre-2000 date (98 > 26); the year compare does not")
  void preTwoThousandIsNeverFlagged() {
    for (int year = 1900; year < 2000; year++) {
      assertThat(FutureEnrolment.flagged(LocalDate.of(year, 6, 1), d("2026-03-31")))
          .as("enrolment year %s", year)
          .isFalse();
    }
  }

  @Test
  @DisplayName(
      "[MAM-78 AC-5] the legacy two-digit test could never flag a date after 2099 (01 is not > 26); the year compare does")
  void afterTwoThousandNinetyNineIsFlagged() {
    for (int year = 2100; year < 2110; year++) {
      assertThat(FutureEnrolment.flagged(LocalDate.of(year, 1, 1), d("2026-03-31")))
          .as("enrolment year %s", year)
          .isTrue();
    }
  }

  @Test
  @DisplayName("[MAM-78 AC-1] a provider without an enrolment date is not flagged")
  void missingDateIsNotFlagged() {
    assertThat(FutureEnrolment.flagged(null, d("2026-03-31"))).isFalse();
  }
}
