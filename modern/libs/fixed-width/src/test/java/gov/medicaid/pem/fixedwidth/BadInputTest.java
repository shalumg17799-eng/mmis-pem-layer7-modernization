package gov.medicaid.pem.fixedwidth;

import static gov.medicaid.pem.fixedwidth.Samples.GR_MASTER;
import static gov.medicaid.pem.fixedwidth.Samples.IN_MASTER;
import static gov.medicaid.pem.fixedwidth.Samples.RESERVED;
import static gov.medicaid.pem.fixedwidth.Samples.bytes;
import static gov.medicaid.pem.fixedwidth.Samples.overwrite;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * MAM-42 AC-6 (D-024, D-007, D-004): a record of the wrong length, a non-numeric character in a
 * numeric field or an impossible date is rejected with an error that names the layout, the record
 * number and the field. Nothing is truncated, padded or defaulted.
 *
 * <p>Record number 4321 is used throughout so that it cannot be mistaken for a length or a byte
 * position in a message.
 */
class BadInputTest {

  private static final long RECORD_NUMBER = 4321;

  static Stream<Arguments> wrongLengths() {
    return Stream.of(
        arguments("PRVRECRD", 130, 0),
        arguments("PRVRECRD", 130, 121),
        arguments("PRVRECRD", 130, 129),
        arguments("PRVRECRD", 130, 131),
        arguments("PRVRECRD", 130, 200),
        arguments("APPINPUT", 200, 130),
        arguments("APPINPUT", 200, 199),
        arguments("APPINPUT", 200, 201),
        arguments("APPERROR", 250, 249),
        arguments("SCRNREC", 67, 66),
        arguments("SANCIN", 80, 79),
        arguments("SANCIN", 80, 81),
        arguments("REVALQ", 80, 79),
        arguments("TMSISOUT", 120, 119),
        arguments("DIROUT", 100, 99));
  }

  /**
   * layout, a valid sample of it, the 1-based byte to damage, the damaged text, the field that
   * holds it.
   */
  static Stream<Arguments> nonNumericDates() {
    return Stream.of(
        arguments("PRVRECRD", IN_MASTER, 35, "2026O115", "PRV-EFFECTIVE-DATE"),
        arguments("PRVRECRD", GR_MASTER, 43, "        ", "PRV-REVAL-DUE-DATE"),
        arguments("APPINPUT", IN_MASTER + RESERVED, 35, "20260-15", "PRV-EFFECTIVE-DATE"),
        arguments("SANCIN", Samples.SANCTION_TERMINATE, 52, "2026-1-5", "SAN-EFF-DATE"),
        arguments("SCRNREC", Samples.SCREEN_PASS, 20, "2026011X", "SCR-VERDICT-DATE"),
        arguments("REVALQ", Samples.REVAL, 11, "20260A15", "REV-DUE-DATE"),
        arguments("TMSISOUT", Samples.TMSIS, 26, "26A15", "WS-T-PERIOD-YYDDD"));
  }

  @ParameterizedTest(name = "{0}: {2} bytes instead of {1} names the layout and the record")
  @MethodSource("wrongLengths")
  @DisplayName(
      "[MAM-42 AC-6] a record of the wrong length is rejected with the layout and the record number")
  void wrongLengthNamesLayoutAndRecord(String layout, int expected, int actual) {
    assertThatThrownBy(
            () -> FixedWidthCodec.forLayout(layout).read(Samples.spaces(actual), RECORD_NUMBER))
        .isInstanceOfSatisfying(
            FixedWidthException.class,
            ex -> {
              assertThat(ex.layout()).isEqualTo(layout);
              assertThat(ex.recordNumber()).isEqualTo(RECORD_NUMBER);
              assertThat(ex).hasMessageContaining(layout).hasMessageContaining("4321");
            });
  }

  @ParameterizedTest(name = "{0}: {2} bytes instead of {1} states both lengths")
  @MethodSource("wrongLengths")
  @DisplayName(
      "[MAM-42 AC-6] a record of the wrong length is rejected with the expected and the actual length")
  void wrongLengthStatesBothLengths(String layout, int expected, int actual) {
    assertThatThrownBy(
            () -> FixedWidthCodec.forLayout(layout).read(Samples.spaces(actual), RECORD_NUMBER))
        .isInstanceOf(FixedWidthException.class)
        .hasMessageContaining(String.valueOf(expected))
        .hasMessageContaining(String.valueOf(actual));
  }

  @ParameterizedTest(name = "{0}: {3} in {4} names layout, record and field")
  @MethodSource("nonNumericDates")
  @DisplayName(
      "[MAM-42 AC-6] a non-numeric character in a numeric field is rejected with layout, record number and field")
  void nonNumericNamesLayoutRecordAndField(
      String layout, String sample, int firstByte, String damaged, String field) {
    byte[] record = overwrite(sample, firstByte, damaged);
    assertThatThrownBy(() -> FixedWidthCodec.forLayout(layout).read(record, RECORD_NUMBER))
        .isInstanceOfSatisfying(
            FixedWidthException.class,
            ex -> {
              assertThat(ex.layout()).isEqualTo(layout);
              assertThat(ex.recordNumber()).isEqualTo(RECORD_NUMBER);
              assertThat(ex.field()).isEqualTo(field);
              assertThat(ex)
                  .hasMessageContaining(layout)
                  .hasMessageContaining("4321")
                  .hasMessageContaining(field);
            });
  }

  @ParameterizedTest(name = "date {0} is impossible")
  @ValueSource(
      strings = {
        "20260230",
        "20260229",
        "20261301",
        "20260431",
        "20260100",
        "20260132",
        "20260015",
        "19000229",
        "21000229"
      })
  @DisplayName(
      "[MAM-42 AC-6] an impossible CCYYMMDD date such as 20260230 is rejected with layout, record number and field")
  void impossibleDateNamesLayoutRecordAndField(String date) {
    byte[] record = overwrite(IN_MASTER, 35, date);
    assertThatThrownBy(() -> FixedWidthCodec.forLayout("PRVRECRD").read(record, RECORD_NUMBER))
        .isInstanceOfSatisfying(
            FixedWidthException.class,
            ex -> {
              assertThat(ex.layout()).isEqualTo("PRVRECRD");
              assertThat(ex.recordNumber()).isEqualTo(RECORD_NUMBER);
              assertThat(ex.field()).isEqualTo("PRV-EFFECTIVE-DATE");
              assertThat(ex)
                  .hasMessageContaining("4321")
                  .hasMessageContaining("PRV-EFFECTIVE-DATE");
            });
  }

  @ParameterizedTest(name = "date {0} is possible")
  @ValueSource(strings = {"20280229", "20000229", "20261231", "20260101", "20260430"})
  @DisplayName(
      "[MAM-42 AC-6] the edges of valid dates are accepted: leap day in 2028 and 2000, month ends")
  void validDateEdgesAreAccepted(String date) {
    byte[] record = overwrite(IN_MASTER, 35, date);
    FixedRecord read = FixedWidthCodec.forLayout("PRVRECRD").read(record, RECORD_NUMBER);
    assertThat(read.getDate("PRV-EFFECTIVE-DATE"))
        .isEqualTo(LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE));
  }

  @Test
  @DisplayName(
      "[MAM-42 AC-6] an impossible date in the second numeric field is reported on that field")
  void impossibleDateNamesTheSecondDateField() {
    byte[] record = overwrite(IN_MASTER, 43, "20310230");
    assertThatThrownBy(() -> FixedWidthCodec.forLayout("PRVRECRD").read(record, RECORD_NUMBER))
        .isInstanceOfSatisfying(
            FixedWidthException.class,
            ex -> assertThat(ex.field()).isEqualTo("PRV-REVAL-DUE-DATE"));
  }

  @Test
  @DisplayName(
      "[MAM-42 AC-6] the error carries the number of the bad record, not of the first record read")
  void errorCarriesTheNumberOfTheBadRecord() {
    FixedWidthCodec codec = FixedWidthCodec.forLayout("SANCIN");
    assertThat(codec.read(bytes(Samples.SANCTION_TERMINATE), 1).get("SAN-NPI"))
        .isEqualTo("1003000444");
    byte[] bad = overwrite(Samples.SANCTION_TERMINATE, 52, "20260230");
    assertThatThrownBy(() -> codec.read(bad, 2))
        .isInstanceOfSatisfying(
            FixedWidthException.class, ex -> assertThat(ex.recordNumber()).isEqualTo(2));
  }
}
