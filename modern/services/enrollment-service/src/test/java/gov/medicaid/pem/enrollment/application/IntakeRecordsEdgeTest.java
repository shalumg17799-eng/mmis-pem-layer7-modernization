package gov.medicaid.pem.enrollment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.medicaid.pem.enrollment.AppInputFile;
import gov.medicaid.pem.enrollment.application.IntakeFakes.Intake;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How the intake cuts an APPINPUT file into records, at the edges (MAM-56 AC-3, MAM-57 AC-5): an
 * empty first line, a file that is only a line end, a CR LF file without a final line end, a file
 * of blocks that ends in a one-byte block, a line that is longer than a block. A file that has any
 * line end is read line by line, one that has none in blocks of 200 bytes; an empty line is a
 * record of zero bytes, which is unreadable (MAM-57 AC-5), never silently skipped.
 */
class IntakeRecordsEdgeTest {

  static List<String> texts(String file) {
    return IntakeLoad.records(file.getBytes(StandardCharsets.ISO_8859_1)).stream()
        .map(r -> new String(r, StandardCharsets.ISO_8859_1))
        .toList();
  }

  @Test
  @DisplayName("[MAM-56 AC-3] a file that starts with an empty line has an empty first record")
  void emptyFirstLine() {
    assertThat(texts("\nAAA\n")).containsExactly("", "AAA");
  }

  @Test
  @DisplayName("[MAM-57 AC-5] a file that is only a line end is one record of zero bytes")
  void onlyALineEnd() {
    assertThat(texts("\n")).containsExactly("");
  }

  @Test
  @DisplayName("[MAM-57 AC-5] a file that is only a CR LF is one record of zero bytes")
  void onlyACrLf() {
    assertThat(texts("\r\n")).containsExactly("");
  }

  @Test
  @DisplayName("[MAM-56 AC-3] a CR LF file whose last line has no line end has both records")
  void crLfWithoutFinalLineEnd() {
    assertThat(texts("AAA\r\nBBB")).containsExactly("AAA", "BBB");
  }

  @Test
  @DisplayName("[MAM-56 AC-3] two empty lines are two records of zero bytes, none is dropped")
  void twoEmptyLines() {
    assertThat(texts("\n\n")).containsExactly("", "");
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-3] a file of 401 bytes with no line end is two blocks of 200 and one byte")
  void oneByteLastBlock() {
    List<String> records = texts("A".repeat(200) + "B".repeat(200) + "C");
    assertThat(records).hasSize(3);
    assertThat(records.get(0)).isEqualTo("A".repeat(200));
    assertThat(records.get(1)).isEqualTo("B".repeat(200));
    assertThat(records.get(2)).isEqualTo("C");
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-5] a file with line ends is not cut into blocks: a line of 250 bytes is one record of 250 bytes")
  void longLineIsOneRecord() {
    List<String> records = texts("X".repeat(250) + "\nY\n");
    assertThat(records).hasSize(2);
    assertThat(records.get(0)).hasSize(250);
    assertThat(records.get(1)).isEqualTo("Y");
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-5] an empty line between two records ends the run FAILED naming record 2 and zero bytes")
  void emptyLineEndsTheRun() {
    Intake in = new Intake();
    byte[] file =
        ("%s\n\n%s\n".formatted(AppInputFile.valid("1003000111"), AppInputFile.valid("1003000222")))
            .getBytes(StandardCharsets.ISO_8859_1);
    assertThatThrownBy(() -> in.load.process(UUID.randomUUID(), IntakeFakes.DATE, file, "admin1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("record 2 is 0 bytes, not 200");
    assertThat(in.runs.lastCounts()).containsExactly(2, 1, 1, 0);
    assertThat(in.store.saved).hasSize(1);
  }
}
