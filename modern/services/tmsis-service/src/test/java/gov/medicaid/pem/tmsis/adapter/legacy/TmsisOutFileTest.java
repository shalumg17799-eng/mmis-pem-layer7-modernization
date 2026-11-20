package gov.medicaid.pem.tmsis.adapter.legacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.medicaid.pem.fixedwidth.FixedRecord;
import gov.medicaid.pem.fixedwidth.FixedWidthCodec;
import gov.medicaid.pem.fixedwidth.FixedWidthException;
import gov.medicaid.pem.tmsis.Tmsisout;
import gov.medicaid.pem.tmsis.domain.TmsisRecord;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * MAM-71 AC-1, AC-2, AC-3 and AC-4: the TMSISOUT record writer (PRVTMS01:WS-TMSIS-DETAIL, 120
 * bytes, ADR-005) checked byte by byte against modern/docs/analysis/data-layouts.md: bytes 1-5
 * 'PRV01', 6-14 the Medicaid ID, 15-24 the NPI, 25 the status, 26-30 the YYDDD period, 31-120
 * spaces; no header, no trailer, no line ends.
 */
class TmsisOutFileTest {

  final TmsisOutFile writer = new TmsisOutFile();

  static final TmsisRecord ONE = new TmsisRecord("M00000111", "1003000111", "A", "26090");
  static final TmsisRecord THREE = new TmsisRecord("M00000333", "1003000333", "A", "26090");

  static String text(byte[] file) {
    return new String(file, StandardCharsets.ISO_8859_1);
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-2] one record is 120 bytes: PRV01, M00000111, 1003000111, A, 26090 and 90 spaces")
  void wholeRecord() {
    byte[] file = writer.write(List.of(ONE));
    assertThat(file).hasSize(120);
    assertThat(text(file)).isEqualTo("PRV01" + "M00000111" + "1003000111" + "A" + "26090" + " ".repeat(90));
  }

  static Stream<Arguments> fields() {
    return Stream.of(
        Arguments.of("record type", 1, 5, "PRV01"),
        Arguments.of("Medicaid ID", 6, 14, "M00000111"),
        Arguments.of("NPI", 15, 24, "1003000111"),
        Arguments.of("status", 25, 25, "A"),
        Arguments.of("period", 26, 30, "26090"),
        Arguments.of("filler", 31, 120, " ".repeat(90)));
  }

  @ParameterizedTest(name = "[MAM-71 AC-2] bytes {1}-{2} hold the {0}")
  @MethodSource("fields")
  void fieldBytes(String field, int from, int to, String expected) {
    String record = Tmsisout.records(writer.write(List.of(ONE))).get(0);
    assertThat(Tmsisout.bytes(record, from, to)).isEqualTo(expected);
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-1] records are written side by side in the order given, with no header, no trailer and no line end")
  void noFramingAndOrderKept() {
    byte[] file = writer.write(List.of(ONE, THREE, ONE));
    assertThat(file).hasSize(360);
    assertThat(text(file)).doesNotContain("\n").doesNotContain("\r");
    assertThat(Tmsisout.npis(file)).containsExactly("1003000111", "1003000333", "1003000111");
    assertThat(text(file).substring(0, 5)).isEqualTo("PRV01");
    assertThat(text(file).substring(120, 125)).isEqualTo("PRV01");
  }

  @Test
  @DisplayName("[MAM-75 AC-2] no records give an empty file")
  void emptyFile() {
    assertThat(writer.write(List.of())).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-2] the status byte is A in every record and bytes 31-120 are spaces in every record")
  void everyRecordHasStatusAAndSpaces() {
    List<String> records = Tmsisout.records(writer.write(List.of(ONE, THREE)));
    assertThat(records)
        .allSatisfy(
            r -> {
              assertThat(Tmsisout.status(r)).isEqualTo("A");
              assertThat(Tmsisout.filler(r)).isEqualTo(" ".repeat(90));
            });
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-2] the Medicaid ID and the NPI are copied unchanged into bytes 6-14 and 15-24")
  void idsCopiedUnchanged() {
    TmsisRecord r = new TmsisRecord("AB1234567", "1234567890", "A", "26090");
    String record = Tmsisout.records(writer.write(List.of(r))).get(0);
    assertThat(Tmsisout.medicaidId(record)).isEqualTo("AB1234567");
    assertThat(Tmsisout.npi(record)).isEqualTo("1234567890");
  }

  @Test
  @DisplayName("[MAM-71 AC-2] a Medicaid ID shorter than 9 characters is padded with spaces on the right")
  void shortMedicaidIdPadded() {
    TmsisRecord r = new TmsisRecord("M123", "1003000111", "A", "26090");
    String record = Tmsisout.records(writer.write(List.of(r))).get(0);
    assertThat(Tmsisout.medicaidId(record)).isEqualTo("M123     ");
    assertThat(Tmsisout.npi(record)).isEqualTo("1003000111");
  }

  @Test
  @DisplayName("[MAM-71 AC-2] a missing Medicaid ID is nine spaces")
  void missingMedicaidId() {
    TmsisRecord r = new TmsisRecord(null, "1003000111", "A", "26090");
    String record = Tmsisout.records(writer.write(List.of(r))).get(0);
    assertThat(Tmsisout.medicaidId(record)).isEqualTo(" ".repeat(9));
    assertThat(Tmsisout.npi(record)).isEqualTo("1003000111");
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-2] a value that does not fit its field is an error, never silently cut (D-024)")
  void tooLongValueIsAnError() {
    TmsisRecord r = new TmsisRecord("M000001119999", "1003000111", "A", "26090");
    assertThatThrownBy(() -> writer.write(List.of(r))).isInstanceOf(FixedWidthException.class);
  }

  @ParameterizedTest(name = "[MAM-71 AC-4] the period {0} sits in bytes 26-30")
  @MethodSource("periods")
  void periodBytes(String period) {
    TmsisRecord r = new TmsisRecord("M00000111", "1003000111", "A", period);
    String record = Tmsisout.records(writer.write(List.of(r))).get(0);
    assertThat(Tmsisout.period(record)).isEqualTo(period);
    assertThat(Tmsisout.status(record)).isEqualTo("A");
    assertThat(Tmsisout.filler(record)).isEqualTo(" ".repeat(90));
  }

  static Stream<String> periods() {
    return Stream.of("26090", "26005", "26089", "28366", "00001", "99365");
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-3] the record reads back through the codec and writes back to the same bytes")
  void roundTrip() {
    FixedWidthCodec codec = FixedWidthCodec.forLayout("TMSISOUT");
    byte[] record = writer.write(List.of(ONE));
    FixedRecord read = codec.read(record, 1);
    assertThat(read.get("WS-T-PROVID")).isEqualTo("M00000111");
    assertThat(read.get("WS-T-NPI")).isEqualTo("1003000111");
    assertThat(read.get("WS-T-STATUS")).isEqualTo("A");
    assertThat(read.get("WS-T-PERIOD-YYDDD")).isEqualTo("26090");
    assertThat(codec.write(read)).isEqualTo(record);
  }

  @Test
  @DisplayName("[MAM-71 AC-2] the TMSISOUT layout in the codec is the documented 120-byte layout")
  void layoutIsTheDocumentedOne() {
    var layout = FixedWidthCodec.forLayout("TMSISOUT").layout();
    assertThat(layout.length()).isEqualTo(120);
    assertThat(layout.fields())
        .extracting(f -> f.name() + ":" + f.start() + ":" + f.length())
        .containsExactly(
            "FILLER:1:5",
            "WS-T-PROVID:6:9",
            "WS-T-NPI:15:10",
            "WS-T-STATUS:25:1",
            "WS-T-PERIOD-YYDDD:26:5",
            "FILLER:31:90");
  }

  @Test
  @DisplayName("[MAM-71 AC-2] the test helper and the writer agree on the whole record")
  void helperAgreesWithTheWriter() {
    assertThat(text(writer.write(List.of(ONE))))
        .isEqualTo(Tmsisout.expected("M00000111", "1003000111", "26090"));
  }
}
