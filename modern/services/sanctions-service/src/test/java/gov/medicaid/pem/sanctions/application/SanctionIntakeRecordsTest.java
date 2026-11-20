package gov.medicaid.pem.sanctions.application;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.sanctions.SancinFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MAM-55 AC-2 and AC-6: the uploaded SANCIN file is cut into records (PRVSAN01 reads one record at
 * a time until end of file): one per line, or blocks of 80 bytes when the file has no line end. A
 * record that is not 80 bytes long stays as it is, so the run can name it (AC-6).
 */
class SanctionIntakeRecordsTest {

  static final String A = SancinFile.record("1003000111", "T", "SYNTHETIC TEST REASON", "20260331");
  static final String B = SancinFile.record("1003000333", "S", "SYNTHETIC TEST REASON", "20260331");
  static final String C = SancinFile.record("1003000222", "R", "SYNTHETIC TEST REASON", "20260331");

  static List<String> text(List<byte[]> records) {
    return records.stream().map(r -> new String(r, StandardCharsets.ISO_8859_1)).toList();
  }

  static byte[] bytes(String s) {
    return s.getBytes(StandardCharsets.ISO_8859_1);
  }

  @Test
  @DisplayName("[MAM-55 AC-2] a record of the test fixture is 80 bytes")
  void fixtureLength() {
    assertThat(A).hasSize(80);
    assertThat(SancinFile.LRECL).isEqualTo(80);
  }

  @Test
  @DisplayName("[MAM-55 AC-2] a file with LF line ends gives one record per line, in file order")
  void lf() {
    assertThat(text(SanctionIntake.records(SancinFile.lines(A, B, C)))).containsExactly(A, B, C);
  }

  @Test
  @DisplayName("[MAM-55 AC-2] the last record needs no line end")
  void lastLineWithoutLineEnd() {
    byte[] file = bytes(A + "\n" + B);
    assertThat(text(SanctionIntake.records(file))).containsExactly(A, B);
  }

  @Test
  @DisplayName("[MAM-55 AC-2] the line end after the last record does not add an empty record")
  void noEmptyRecordAfterTheLastLineEnd() {
    assertThat(SanctionIntake.records(SancinFile.lines(A))).hasSize(1);
  }

  @Test
  @DisplayName("[MAM-55 AC-2] CR LF line ends are removed, so each record is 80 bytes")
  void crlf() {
    List<byte[]> records = SanctionIntake.records(SancinFile.crlf(A, B));
    assertThat(text(records)).containsExactly(A, B);
    assertThat(records).allSatisfy(r -> assertThat(r).hasSize(80));
  }

  @Test
  @DisplayName("[MAM-55 AC-2] a CR that is not in front of an LF belongs to the record")
  void loneCarriageReturnStays() {
    assertThat(text(SanctionIntake.records(bytes("AB\rCD\nEF\n")))).containsExactly("AB\rCD", "EF");
  }

  @Test
  @DisplayName("[MAM-55 AC-2] a file with no line end is read as blocks of 80 bytes")
  void blocks() {
    assertThat(text(SanctionIntake.records(SancinFile.blocks(A, B, C)))).containsExactly(A, B, C);
  }

  @Test
  @DisplayName("[MAM-55 AC-6] the last block of a file with no line end may be short")
  void shortLastBlock() {
    byte[] file = bytes(A + B + C.substring(0, 50));
    List<byte[]> records = SanctionIntake.records(file);
    assertThat(records).hasSize(3);
    assertThat(records.get(2)).hasSize(50);
  }

  @Test
  @DisplayName("[MAM-55 AC-2] a file of exactly one block gives one record")
  void oneBlock() {
    assertThat(text(SanctionIntake.records(SancinFile.blocks(A)))).containsExactly(A);
  }

  @Test
  @DisplayName("[MAM-55 AC-2] an empty file has no records")
  void emptyFile() {
    assertThat(SanctionIntake.records(new byte[0])).isEmpty();
  }

  @Test
  @DisplayName("[MAM-55 AC-6] a short line stays a short record, so the run can name it")
  void shortLine() {
    List<byte[]> records = SanctionIntake.records(SancinFile.lines(A, SancinFile.cut(B, 60), C));
    assertThat(records).extracting(r -> r.length).containsExactly(80, 60, 80);
  }

  @Test
  @DisplayName("[MAM-55 AC-6] a line longer than 80 bytes stays a long record")
  void longLine() {
    List<byte[]> records = SanctionIntake.records(SancinFile.lines(A + "X", B));
    assertThat(records).extracting(r -> r.length).containsExactly(81, 80);
  }

  @Test
  @DisplayName("[MAM-55 AC-6] an empty line in the file is a record of 0 bytes, not skipped")
  void emptyLine() {
    List<byte[]> records = SanctionIntake.records(bytes(A + "\n\n" + B + "\n"));
    assertThat(records).extracting(r -> r.length).containsExactly(80, 0, 80);
  }

  @Test
  @DisplayName("[MAM-55 AC-6] a file that is only a line end has one empty record")
  void onlyALineEnd() {
    assertThat(SanctionIntake.records(bytes("\n"))).extracting(r -> r.length).containsExactly(0);
    assertThat(SanctionIntake.records(bytes("\r\n"))).extracting(r -> r.length).containsExactly(0);
  }

  @Test
  @DisplayName("[MAM-55 AC-2] the bytes of a record are kept as they are (one byte per character)")
  void bytesAreKept() {
    String withAccent = SancinFile.record("1003000111", "T", "REASON é BYTE", "20260331");
    List<byte[]> records = SanctionIntake.records(SancinFile.lines(withAccent));
    assertThat(records.get(0)).isEqualTo(bytes(withAccent));
    assertThat(records.get(0)).hasSize(80);
  }
}
