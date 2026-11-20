package gov.medicaid.pem.fixedwidth;

import static gov.medicaid.pem.fixedwidth.Samples.TMSIS;
import static gov.medicaid.pem.fixedwidth.Samples.overwrite;
import static gov.medicaid.pem.fixedwidth.Samples.slice;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * MAM-42 AC-4 (dates): 2026-01-15 is 20260115 in a CCYYMMDD field and 26015 in a YYDDD field, and a
 * YYDDD value is only read when the caller gives the century (D-004: no 2-digit-year pivot; D-015:
 * YYDDD only in the legacy T-MSIS layout). "Century" here is the CC of CCYY: 20 for 2000-2099, 19
 * for 1900-1999.
 */
class DateFieldsTest {

  private final FixedWidthCodec tmsis = FixedWidthCodec.forLayout("TMSISOUT");
  private final FixedWidthCodec master = FixedWidthCodec.forLayout("PRVRECRD");

  @Test
  @DisplayName("[MAM-42 AC-4] the date 2026-01-15 is written as 20260115 in a CCYYMMDD field")
  void ccyymmddIsWrittenWithFourDigitYear() {
    FixedRecord record = Samples.individualRecord(master);
    record.setDate("PRV-EFFECTIVE-DATE", LocalDate.of(2026, 1, 15));
    assertThat(slice(master.write(record), 35, 8)).isEqualTo("20260115");
  }

  @Test
  @DisplayName("[MAM-42 AC-4] the date 2026-01-15 is written as 26015 in a YYDDD field")
  void yydddIsWrittenAsTwoDigitYearAndDayOfYear() {
    FixedRecord record = tmsis.newRecord();
    record.setYyddd("WS-T-PERIOD-YYDDD", LocalDate.of(2026, 1, 15));
    assertThat(slice(tmsis.write(record), 26, 5)).isEqualTo("26015");
  }

  @ParameterizedTest(name = "{0} is {1} in YYDDD, century {2}")
  @CsvSource({
    "2026-01-15,26015,20",
    "2026-03-01,26060,20",
    "2028-03-01,28061,20",
    "2026-12-31,26365,20",
    "2028-12-31,28366,20",
    "2000-01-01,00001,20",
    "2000-02-29,00060,20",
    "2000-12-31,00366,20",
    "1900-03-01,00060,19",
    "1999-12-31,99365,19"
  })
  @DisplayName(
      "[MAM-42 AC-4] YYDDD is the two-digit year and the zero-filled day of the year, leap years included")
  void yydddIsWritten(LocalDate date, String yyddd, int century) {
    FixedRecord record = tmsis.newRecord();
    record.setYyddd("WS-T-PERIOD-YYDDD", date);
    assertThat(slice(tmsis.write(record), 26, 5)).isEqualTo(yyddd);
  }

  @ParameterizedTest(name = "{1} with century {2} is {0}")
  @CsvSource({
    "2026-01-15,26015,20",
    "2026-03-01,26060,20",
    "2028-03-01,28061,20",
    "2026-12-31,26365,20",
    "2028-12-31,28366,20",
    "2000-01-01,00001,20",
    "2000-02-29,00060,20",
    "2000-12-31,00366,20",
    "1900-03-01,00060,19",
    "1999-12-31,99365,19"
  })
  @DisplayName("[MAM-42 AC-4] a YYDDD value is read with the century the caller gives")
  void yydddIsReadWithTheCallersCentury(LocalDate expected, String yyddd, int century) {
    FixedRecord record = tmsis.read(overwrite(TMSIS, 26, yyddd), 1);
    assertThat(record.getYyddd("WS-T-PERIOD-YYDDD", century)).isEqualTo(expected);
  }

  @Test
  @DisplayName(
      "[MAM-42 AC-4] the same YYDDD 26015 is 2026-01-15 or 1926-01-15 by the century given: no fixed pivot year")
  void centuryDecidesTheYear() {
    FixedRecord record = tmsis.read(Samples.bytes(TMSIS), 1);
    assertThat(record.getYyddd("WS-T-PERIOD-YYDDD", 20)).isEqualTo(LocalDate.of(2026, 1, 15));
    assertThat(record.getYyddd("WS-T-PERIOD-YYDDD", 19)).isEqualTo(LocalDate.of(1926, 1, 15));
  }

  @Test
  @DisplayName("[MAM-42 AC-4] reading a YYDDD value without a century is not offered")
  void noYydddReaderWithoutACentury() {
    assertThat(Arrays.stream(FixedRecord.class.getMethods()))
        .filteredOn(method -> method.getName().equals("getYyddd"))
        .isNotEmpty()
        .allMatch(this::takesFieldAndCentury);
  }

  @Test
  @DisplayName("[MAM-42 AC-4] a YYDDD field cannot be read as a CCYYMMDD date")
  void yydddFieldIsNotACcyymmddDate() {
    FixedRecord record = tmsis.read(Samples.bytes(TMSIS), 1);
    assertThatThrownBy(() -> record.getDate("WS-T-PERIOD-YYDDD"))
        .isInstanceOfSatisfying(
            FixedWidthException.class, ex -> assertThat(ex.field()).isEqualTo("WS-T-PERIOD-YYDDD"));
  }

  @ParameterizedTest(name = "YYDDD {0} with century {1} is not a date")
  @CsvSource({"26366,20", "27366,20", "00366,19", "26000,20", "26367,20", "26999,20"})
  @DisplayName(
      "[MAM-42 AC-4] a YYDDD day that does not exist in that year is an error (day 366 only in a leap year)")
  void impossibleYydddIsRejected(String yyddd, int century) {
    byte[] record = overwrite(TMSIS, 26, yyddd);
    assertThatThrownBy(() -> tmsis.read(record, 1).getYyddd("WS-T-PERIOD-YYDDD", century))
        .isInstanceOfSatisfying(
            FixedWidthException.class, ex -> assertThat(ex.field()).isEqualTo("WS-T-PERIOD-YYDDD"));
  }

  @Test
  @DisplayName("[MAM-42 AC-4] a CCYYMMDD field reads back as the date")
  void ccyymmddIsRead() {
    FixedRecord record =
        FixedWidthCodec.forLayout("SCRNREC").read(Samples.bytes(Samples.SCREEN_PASS), 1);
    assertThat(record.getDate("SCR-VERDICT-DATE")).isEqualTo(LocalDate.of(2026, 1, 15));
  }

  private boolean takesFieldAndCentury(Method method) {
    return method.getParameterCount() == 2
        && method.getParameterTypes()[0] == String.class
        && method.getParameterTypes()[1] == int.class;
  }
}
