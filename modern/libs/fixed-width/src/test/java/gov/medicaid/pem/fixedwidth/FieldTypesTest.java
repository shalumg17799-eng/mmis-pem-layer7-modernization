package gov.medicaid.pem.fixedwidth;

import static gov.medicaid.pem.fixedwidth.Samples.slice;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * MAM-42 AC-4: the supported field types. The legacy layouts have no plain numeric display field,
 * so the numeric and alphanumeric rules are checked on a small synthetic layout built in the test
 * (not exported from the graph); the date types are checked in {@link DateFieldsTest}.
 */
class FieldTypesTest {

  /**
   * TEXT bytes 1-10, COUNT bytes 11-17 (numeric display), WHEN bytes 18-25 (CCYYMMDD), JUL bytes
   * 26-30 (YYDDD).
   */
  static final RecordLayout SYNTHETIC =
      new RecordLayout(
          "SYNTHETIC",
          30,
          "synthetic layout for unit tests, not exported from the graph",
          null,
          List.of(
              new FieldDef("TEXT", 1, 10, FieldType.ALPHANUMERIC, Set.of()),
              new FieldDef("COUNT", 11, 7, FieldType.NUMERIC, Set.of()),
              new FieldDef("WHEN", 18, 8, FieldType.DATE_CCYYMMDD, Set.of()),
              new FieldDef("JUL", 26, 5, FieldType.DATE_YYDDD, Set.of())));

  private final FixedWidthCodec codec = FixedWidthCodec.forLayout(SYNTHETIC);

  private FixedRecord filled() {
    FixedRecord record = codec.newRecord();
    record.set("TEXT", "ABC");
    record.setNumber("COUNT", 42);
    record.setDate("WHEN", LocalDate.of(2026, 1, 15));
    record.setYyddd("JUL", LocalDate.of(2026, 1, 15));
    return record;
  }

  @ParameterizedTest(name = "''{0}'' is written as ''{1}''")
  @CsvSource({"ABC,'ABC       '", "ABCDEFGHIJ,ABCDEFGHIJ", "A,'A         '", "'','          '"})
  @DisplayName("[MAM-42 AC-4] an alphanumeric field is right-padded with spaces")
  void alphanumericIsRightPadded(String value, String expected) {
    FixedRecord record = filled();
    record.set("TEXT", value);
    assertThat(slice(codec.write(record), 1, 10)).isEqualTo(expected);
  }

  @Test
  @DisplayName(
      "[MAM-42 AC-4] an alphanumeric value longer than its field is rejected, never truncated")
  void alphanumericTooLongIsRejected() {
    FixedRecord record = filled();
    assertThatThrownBy(() -> record.set("TEXT", "ABCDEFGHIJK"))
        .isInstanceOfSatisfying(
            FixedWidthException.class,
            ex -> {
              assertThat(ex.layout()).isEqualTo("SYNTHETIC");
              assertThat(ex.field()).isEqualTo("TEXT");
            });
  }

  @ParameterizedTest(name = "{0} is written as {1}")
  @CsvSource({"42,0000042", "0,0000000", "7,0000007", "1234567,1234567", "9999999,9999999"})
  @DisplayName("[MAM-42 AC-4] a numeric display field is left zero-filled")
  void numericIsLeftZeroFilled(long value, String expected) {
    FixedRecord record = filled();
    record.setNumber("COUNT", value);
    assertThat(slice(codec.write(record), 11, 7)).isEqualTo(expected);
  }

  @ParameterizedTest(name = "{0} does not fit in 7 digits")
  @ValueSource(longs = {10_000_000L, 12_345_678L, Long.MAX_VALUE})
  @DisplayName(
      "[MAM-42 AC-4] a number with more digits than its field is rejected, never truncated")
  void numericTooLongIsRejected(long value) {
    FixedRecord record = filled();
    assertThatThrownBy(() -> record.setNumber("COUNT", value))
        .isInstanceOfSatisfying(
            FixedWidthException.class, ex -> assertThat(ex.field()).isEqualTo("COUNT"));
  }

  @ParameterizedTest(name = "{0} reads as {1}")
  @CsvSource({"0000042,42", "0000000,0", "1234567,1234567"})
  @DisplayName("[MAM-42 AC-4] a numeric display field reads back as the number")
  void numericReadsBack(String digits, long expected) {
    byte[] record = Samples.bytes("ABC       " + digits + "20260115" + "26015");
    assertThat(codec.read(record, 1).getNumber("COUNT")).isEqualTo(expected);
  }

  @Test
  @DisplayName("[MAM-42 AC-4] a written record is the padded fields side by side: 30 bytes")
  void writtenRecordIsThePaddedFieldsSideBySide() {
    assertThat(codec.write(filled()))
        .isEqualTo(Samples.bytes("ABC       " + "0000042" + "20260115" + "26015"));
  }

  @ParameterizedTest(name = "{0} {1} is {2}")
  @CsvSource({
    "PRVRECRD,PRV-NPI,ALPHANUMERIC",
    "PRVRECRD,PRV-LAST-NAME,ALPHANUMERIC",
    "PRVRECRD,PRV-EFFECTIVE-DATE,DATE_CCYYMMDD",
    "PRVRECRD,PRV-REVAL-DUE-DATE,DATE_CCYYMMDD",
    "SCRNREC,SCR-VERDICT-DATE,DATE_CCYYMMDD",
    "SANCIN,SAN-EFF-DATE,DATE_CCYYMMDD",
    "REVALQ,REV-DUE-DATE,DATE_CCYYMMDD",
    "TMSISOUT,WS-T-NPI,ALPHANUMERIC",
    "TMSISOUT,WS-T-PERIOD-YYDDD,DATE_YYDDD"
  })
  @DisplayName(
      "[MAM-42 AC-4] the exported layouts type their date fields (4-digit CCYYMMDD, D-004; YYDDD only in T-MSIS, D-015)")
  void exportedFieldTypes(String layout, String field, FieldType expected) {
    assertThat(FixedWidthCodec.forLayout(layout).layout().field(field).type()).isEqualTo(expected);
  }

  @ParameterizedTest(name = "{0} has no YYDDD field")
  @ValueSource(
      strings = {"PRVRECRD", "SCRNREC", "APPINPUT", "APPERROR", "SANCIN", "REVALQ", "DIROUT"})
  @DisplayName("[MAM-42 AC-4] YYDDD appears only inside the legacy T-MSIS layout (D-015)")
  void yydddOnlyInTmsis(String layout) {
    assertThat(FixedWidthCodec.forLayout(layout).layout().fields())
        .extracting(FieldDef::type)
        .doesNotContain(FieldType.DATE_YYDDD);
  }
}
