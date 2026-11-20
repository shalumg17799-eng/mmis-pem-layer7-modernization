package gov.medicaid.pem.directory.adapter.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.directory.Dirout;
import gov.medicaid.pem.directory.application.DirectoryEntry;
import gov.medicaid.pem.fixedwidth.FixedRecord;
import gov.medicaid.pem.fixedwidth.FixedWidthCodec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * MAM-67 AC-3, AC-4 and AC-5: the DIROUT record writer (PRVDIR01:DIR-OUT-RECORD, 100 bytes) checked
 * byte by byte against modern/docs/analysis/data-layouts.md.
 */
class DiroutFileTest {

  final DiroutFile writer = new DiroutFile();

  static final DirectoryEntry RIVERA =
      new DirectoryEntry("1003000111", "M00000111", "IN", "L", "A", "MARIA RIVERA", "207Q00000X");
  static final DirectoryEntry SUNSET =
      new DirectoryEntry(
          "1003000333", "M00000333", "FA", "H", "A", "SUNSET HOSPICE INC", "251G00000X");

  static String text(byte[] file) {
    return new String(file, StandardCharsets.ISO_8859_1);
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-4] one entry is one 100-byte record with every field at its legacy bytes")
  void wholeRecord() {
    byte[] file = writer.write(List.of(RIVERA));
    assertThat(text(file))
        .isEqualTo(
            "M00000111"
                + "1003000111"
                + "IN"
                + "L"
                + Dirout.padded("MARIA RIVERA", 60)
                + " ".repeat(18));
    assertThat(file).hasSize(100);
  }

  static Stream<Arguments> fields() {
    return Stream.of(
        Arguments.of("Medicaid ID", 1, 9, "M00000111"),
        Arguments.of("NPI", 10, 19, "1003000111"),
        Arguments.of("type", 20, 21, "IN"),
        Arguments.of("risk tier", 22, 22, "L"),
        Arguments.of("name", 23, 82, Dirout.padded("MARIA RIVERA", 60)),
        Arguments.of("filler", 83, 100, " ".repeat(18)));
  }

  @ParameterizedTest(name = "[MAM-67 AC-4] bytes {1}-{2} hold the {0}")
  @MethodSource("fields")
  void fieldBytes(String field, int from, int to, String expected) {
    String record = Dirout.records(writer.write(List.of(RIVERA))).get(0);
    assertThat(Dirout.bytes(record, from, to)).isEqualTo(expected);
  }

  @Test
  @DisplayName("[MAM-67 AC-4] a facility record carries its type FA and tier H")
  void facilityRecord() {
    String record = Dirout.records(writer.write(List.of(SUNSET))).get(0);
    assertThat(Dirout.type(record)).isEqualTo("FA");
    assertThat(Dirout.tier(record)).isEqualTo("H");
    assertThat(Dirout.medicaidId(record)).isEqualTo("M00000333");
  }

  @Test
  @DisplayName("[MAM-67 AC-5] the name is left-aligned and padded with spaces to 60 bytes")
  void namePadded() {
    String record = Dirout.records(writer.write(List.of(SUNSET))).get(0);
    assertThat(Dirout.name(record)).isEqualTo("SUNSET HOSPICE INC" + " ".repeat(42));
  }

  @Test
  @DisplayName("[MAM-67 AC-3] records are written side by side in the order given")
  void orderKept() {
    List<String> records = Dirout.records(writer.write(List.of(RIVERA, SUNSET, RIVERA)));
    assertThat(records)
        .extracting(Dirout::npi)
        .containsExactly("1003000111", "1003000333", "1003000111");
  }

  @Test
  @DisplayName("[MAM-67 AC-3] the file has no header, no trailer and no line end")
  void noFraming() {
    byte[] file = writer.write(List.of(RIVERA, SUNSET));
    assertThat(file).hasSize(200);
    assertThat(text(file)).doesNotContain("\n").doesNotContain("\r");
    assertThat(text(file).substring(0, 9)).isEqualTo("M00000111");
    assertThat(text(file).substring(100, 109)).isEqualTo("M00000333");
  }

  @Test
  @DisplayName("[MAM-67 AC-3] no entries give an empty file")
  void empty() {
    assertThat(writer.write(List.of())).isEmpty();
  }

  @Test
  @DisplayName("[MAM-67 AC-4] a missing risk tier is one space and a missing Medicaid ID nine")
  void missingValues() {
    DirectoryEntry e = new DirectoryEntry("1003000555", null, "IN", null, "A", "PAT SAMPLE", null);
    String record = Dirout.records(writer.write(List.of(e))).get(0);
    assertThat(Dirout.medicaidId(record)).isEqualTo(" ".repeat(9));
    assertThat(Dirout.tier(record)).isEqualTo(" ");
    assertThat(Dirout.npi(record)).isEqualTo("1003000555");
    assertThat(Dirout.name(record)).isEqualTo(Dirout.padded("PAT SAMPLE", 60));
  }

  @Test
  @DisplayName("[MAM-67 AC-4] an empty value is spaces, as a missing one")
  void emptyValues() {
    DirectoryEntry e = new DirectoryEntry("1003000555", "", "IN", "", "A", "", null);
    String record = Dirout.records(writer.write(List.of(e))).get(0);
    assertThat(Dirout.medicaidId(record)).isEqualTo(" ".repeat(9));
    assertThat(Dirout.tier(record)).isEqualTo(" ");
    assertThat(Dirout.name(record)).isEqualTo(" ".repeat(60));
  }

  @Test
  @DisplayName("[MAM-67 AC-5] a name of exactly 60 characters fills the field and is not cut")
  void nameExactlyFull() {
    String name = "N".repeat(60);
    DirectoryEntry e = new DirectoryEntry("1003000111", "M00000111", "IN", "L", "A", name, null);
    String record = Dirout.records(writer.write(List.of(e))).get(0);
    assertThat(Dirout.name(record)).isEqualTo(name);
    assertThat(Dirout.filler(record)).isEqualTo(" ".repeat(18));
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-4] a value longer than its field is cut on the right, never spilling over")
  void longerValuesCut() {
    DirectoryEntry e =
        new DirectoryEntry(
            "10030001119999", "M000001119999", "INX", "LM", "A", "N".repeat(61), "207Q00000X");
    byte[] file = writer.write(List.of(e));
    assertThat(file).hasSize(100);
    String record = Dirout.records(file).get(0);
    assertThat(Dirout.medicaidId(record)).isEqualTo("M00000111");
    assertThat(Dirout.npi(record)).isEqualTo("1003000111");
    assertThat(Dirout.type(record)).isEqualTo("IN");
    assertThat(Dirout.tier(record)).isEqualTo("L");
    assertThat(Dirout.name(record)).isEqualTo("N".repeat(60));
    assertThat(Dirout.filler(record)).isEqualTo(" ".repeat(18));
  }

  @Test
  @DisplayName("[MAM-67 AC-4] bytes 83-100 are spaces in every record")
  void fillerAlwaysSpaces() {
    List<String> records = Dirout.records(writer.write(List.of(RIVERA, SUNSET)));
    assertThat(records).allSatisfy(r -> assertThat(Dirout.filler(r)).isEqualTo(" ".repeat(18)));
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-4] each record reads back through the codec and writes back to the same bytes")
  void roundTrip() {
    FixedWidthCodec codec = FixedWidthCodec.forLayout("DIROUT");
    byte[] record = writer.write(List.of(RIVERA));
    FixedRecord read = codec.read(record, 1);
    assertThat(read.get("DIR-MEDICAID-ID")).isEqualTo("M00000111");
    assertThat(read.get("DIR-NPI")).isEqualTo("1003000111");
    assertThat(read.get("DIR-TYPE")).isEqualTo("IN");
    assertThat(read.get("DIR-RISK-TIER")).isEqualTo("L");
    assertThat(read.get("DIR-NAME")).isEqualTo("MARIA RIVERA");
    assertThat(codec.write(read)).isEqualTo(record);
  }

  @Test
  @DisplayName("[MAM-67 AC-4] the DIROUT layout in the codec is the documented 100-byte layout")
  void layoutIsTheDocumentedOne() {
    var layout = FixedWidthCodec.forLayout("DIROUT").layout();
    assertThat(layout.length()).isEqualTo(100);
    assertThat(layout.fields())
        .extracting(f -> f.name() + ":" + f.start() + ":" + f.length())
        .containsExactly(
            "DIR-MEDICAID-ID:1:9",
            "DIR-NPI:10:10",
            "DIR-TYPE:20:2",
            "DIR-RISK-TIER:22:1",
            "DIR-NAME:23:60",
            "FILLER:83:18");
  }
}
