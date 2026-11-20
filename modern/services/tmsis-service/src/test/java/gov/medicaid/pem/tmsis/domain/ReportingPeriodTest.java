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
 * MAM-71 AC-3 and AC-4, MAM-75 AC-3 and AC-7: the T-MSIS reporting period is the business date as
 * YYDDD, a two-digit year and a three-digit day of the year (D-015, R-PRVTMS01-cbl-00018, -00019,
 * -00041, -00043, R-PRVTMS01-ADD-07). It names a day, not a month.
 */
class ReportingPeriodTest {

  static Stream<Arguments> acDates() {
    return Stream.of(
        Arguments.of("[MAM-71 AC-3]", "2026-03-31", "26090"),
        Arguments.of("[MAM-71 AC-4]", "2026-01-05", "26005"),
        Arguments.of("[MAM-71 AC-4]", "2026-03-30", "26089"),
        Arguments.of("[MAM-71 AC-4]", "2028-12-31", "28366"),
        Arguments.of("[MAM-75 AC-7]", "2026-04-30", "26120"),
        Arguments.of("[MAM-75 AC-3]", "2026-05-10", "26130"));
  }

  @ParameterizedTest(name = "{0} the business date {1} gives the period {2}")
  @MethodSource("acDates")
  void periodOfTheAcceptanceDates(String ac, String date, String period) {
    assertThat(ReportingPeriod.yyddd(LocalDate.parse(date))).isEqualTo(period);
  }

  static Stream<Arguments> edges() {
    return Stream.of(
        Arguments.of("first day of a year", "2026-01-01", "26001"),
        Arguments.of("day 9, padded to three digits", "2026-01-09", "26009"),
        Arguments.of("day 10, padded to three digits", "2026-01-10", "26010"),
        Arguments.of("day 99", "2026-04-09", "26099"),
        Arguments.of("day 100", "2026-04-10", "26100"),
        Arguments.of("last day of a common year", "2026-12-31", "26365"),
        Arguments.of("29 February of a leap year", "2028-02-29", "28060"),
        Arguments.of("1 March of a leap year", "2028-03-01", "28061"),
        Arguments.of("1 March of a common year", "2026-03-01", "26060"),
        Arguments.of("year 2000, a leap year", "2000-12-31", "00366"),
        Arguments.of("year 2009, one digit year padded", "2009-06-15", "09166"),
        Arguments.of("year 2099, the last year of the century", "2099-12-31", "99365"));
  }

  @ParameterizedTest(name = "[MAM-71 AC-4] {0}: {1} gives {2}")
  @MethodSource("edges")
  void edgeDates(String what, String date, String period) {
    assertThat(ReportingPeriod.yyddd(LocalDate.parse(date))).isEqualTo(period);
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-4] 2026-03-30 and 2026-03-31 are the same month but different periods (a day, not a month)")
  void periodNamesADayNotAMonth() {
    assertThat(ReportingPeriod.yyddd(LocalDate.parse("2026-03-30")))
        .isNotEqualTo(ReportingPeriod.yyddd(LocalDate.parse("2026-03-31")));
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-4] every day of 2026 and of the leap year 2028 gives five digits that read back as the same day")
  void everyDayRoundTrips() {
    for (int year : new int[] {2026, 2028}) {
      for (LocalDate d = LocalDate.of(year, 1, 1); d.getYear() == year; d = d.plusDays(1)) {
        String period = ReportingPeriod.yyddd(d);
        assertThat(period).as("period of %s", d).matches("[0-9]{5}");
        assertThat(2000 + Integer.parseInt(period.substring(0, 2))).isEqualTo(year);
        assertThat(Integer.parseInt(period.substring(2))).isEqualTo(d.getDayOfYear());
      }
    }
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-4] 2028 has 366 days and 2026 has 365: the last periods are 28366 and 26365")
  void yearLengths() {
    assertThat(ReportingPeriod.yyddd(LocalDate.of(2028, 12, 31))).isEqualTo("28366");
    assertThat(ReportingPeriod.yyddd(LocalDate.of(2026, 12, 31))).isEqualTo("26365");
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-3] the period depends on the given date only, never on the system date")
  void sameDateSamePeriod() {
    LocalDate date = LocalDate.of(2026, 3, 31);
    assertThat(ReportingPeriod.yyddd(date)).isEqualTo(ReportingPeriod.yyddd(date));
    assertThat(ReportingPeriod.yyddd(date)).isEqualTo("26090");
  }
}
