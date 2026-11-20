package gov.medicaid.pem.revalidation.adapter.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.fixedwidth.FixedRecord;
import gov.medicaid.pem.fixedwidth.FixedWidthCodec;
import gov.medicaid.pem.revalidation.domain.CaseStatus;
import gov.medicaid.pem.revalidation.domain.RevalidationCase;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The legacy REVALQ file (PRVREV01 REVAL-OUT-RECORD, 80 bytes, ADR-005, D-004) checked byte for
 * byte: NPI in bytes 1-10, the due date as CCYYMMDD in 11-18, RESCREEN-REQUIRED left-justified and
 * space-padded to 20 characters in 19-38, 42 spaces in 39-80, records side by side with no line
 * ends, in ascending NPI order.
 */
class RevalqFileTest {

  static final String ACTION = "RESCREEN-REQUIRED   ";
  static final String FILLER = " ".repeat(42);

  static RevalidationCase opened(String npi, LocalDate due) {
    return new RevalidationCase(
        UUID.randomUUID(), npi, due, CaseStatus.DUE, Instant.parse("2026-11-19T10:00:00Z"), null);
  }

  static String text(byte[] file) {
    return new String(file, StandardCharsets.ISO_8859_1);
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-7] one 80-byte record: NPI, CCYYMMDD due date, RESCREEN-REQUIRED padded to 20, 42 spaces")
  void oneRecord() {
    byte[] file = new RevalqFile().write(List.of(opened("1003000222", LocalDate.of(2026, 2, 28))));

    assertThat(file).hasSize(80);
    assertThat(text(file)).isEqualTo("1003000222" + "20260228" + ACTION + FILLER);
  }

  @Test
  @DisplayName("[MAM-45 AC-7] the byte positions of the fields are 1-10, 11-18, 19-38 and 39-80")
  void fieldPositions() {
    String record =
        text(new RevalqFile().write(List.of(opened("1003000222", LocalDate.of(2031, 3, 31)))));

    assertThat(record.substring(0, 10)).as("REV-NPI, bytes 1-10").isEqualTo("1003000222");
    assertThat(record.substring(10, 18)).as("REV-DUE-DATE, bytes 11-18").isEqualTo("20310331");
    assertThat(record.substring(18, 38)).as("REV-ACTION, bytes 19-38").isEqualTo(ACTION);
    assertThat(record.substring(38, 80)).as("filler, bytes 39-80").isEqualTo(FILLER);
  }

  @Test
  @DisplayName("[MAM-45 AC-7] the action is RESCREEN-REQUIRED followed by 3 spaces (17 + 3 = 20)")
  void actionIsPaddedTo20() {
    String record =
        text(new RevalqFile().write(List.of(opened("1003000222", LocalDate.of(2026, 2, 28)))));

    assertThat("RESCREEN-REQUIRED").hasSize(17);
    assertThat(record.substring(18, 38)).isEqualTo("RESCREEN-REQUIRED" + "   ");
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-7] the records are in ascending NPI order whatever order the cases came in")
  void ascendingNpiOrder() {
    byte[] file =
        new RevalqFile()
            .write(
                List.of(
                    opened("1003000333", LocalDate.of(2026, 3, 31)),
                    opened("1003000111", LocalDate.of(2026, 1, 15)),
                    opened("1003000222", LocalDate.of(2026, 2, 28))));

    String text = text(file);
    assertThat(file).hasSize(240);
    assertThat(text.substring(0, 18)).isEqualTo("1003000111" + "20260115");
    assertThat(text.substring(80, 98)).isEqualTo("1003000222" + "20260228");
    assertThat(text.substring(160, 178)).isEqualTo("1003000333" + "20260331");
  }

  @Test
  @DisplayName("[MAM-45 AC-7] a run that opens no case writes an empty file")
  void emptyFile() {
    assertThat(new RevalqFile().write(List.of())).isEmpty();
  }

  @Test
  @DisplayName("[MAM-45 AC-7] the records have no line end: every byte is part of a field")
  void noLineEnds() {
    byte[] file =
        new RevalqFile()
            .write(
                List.of(
                    opened("1003000222", LocalDate.of(2026, 2, 28)),
                    opened("1003000333", LocalDate.of(2026, 3, 31))));

    assertThat(file).hasSize(160);
    for (byte b : file) {
      assertThat(b).isNotIn((byte) '\n', (byte) '\r', (byte) 0);
    }
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-7] every record of a larger file is exactly 80 bytes and starts with its NPI")
  void everyRecordIs80Bytes() {
    List<RevalidationCase> cases = new ArrayList<>();
    for (int i = 9; i >= 0; i--) {
      cases.add(opened("100300000" + i, LocalDate.of(2026, 1, 1).plusDays(i)));
    }

    byte[] file = new RevalqFile().write(cases);

    assertThat(file).hasSize(800);
    for (int i = 0; i < 10; i++) {
      byte[] record = Arrays.copyOfRange(file, i * 80, (i + 1) * 80);
      assertThat(text(record)).startsWith("100300000" + i);
    }
  }

  @Test
  @DisplayName("[MAM-45 AC-7] the codec of the REVALQ layout reads back what the file holds")
  void theCodecReadsTheRecordsBack() {
    byte[] file =
        new RevalqFile()
            .write(
                List.of(
                    opened("1003000333", LocalDate.of(2031, 3, 31)),
                    opened("1003000222", LocalDate.of(2026, 2, 28))));
    FixedWidthCodec revalq = FixedWidthCodec.forLayout("REVALQ");

    FixedRecord first = revalq.read(Arrays.copyOfRange(file, 0, 80), 1);
    FixedRecord second = revalq.read(Arrays.copyOfRange(file, 80, 160), 2);

    assertThat(first.get("REV-NPI")).isEqualTo("1003000222");
    assertThat(first.getDate("REV-DUE-DATE")).isEqualTo(LocalDate.of(2026, 2, 28));
    assertThat(first.get("REV-ACTION")).isEqualTo("RESCREEN-REQUIRED");
    assertThat(second.get("REV-NPI")).isEqualTo("1003000333");
    assertThat(second.getDate("REV-DUE-DATE")).isEqualTo(LocalDate.of(2031, 3, 31));
  }

  @Test
  @DisplayName("[MAM-45 AC-7] the due date has a 4-digit year (D-004), also before 2000")
  void fourDigitYear() {
    String record =
        text(new RevalqFile().write(List.of(opened("1003000222", LocalDate.of(1999, 12, 31)))));

    assertThat(record.substring(10, 18)).isEqualTo("19991231");
  }

  @Test
  @DisplayName("[MAM-45 AC-7] the list given to the file is not reordered or changed")
  void theInputListIsNotChanged() {
    List<RevalidationCase> given =
        new ArrayList<>(
            List.of(
                opened("1003000333", LocalDate.of(2026, 3, 31)),
                opened("1003000111", LocalDate.of(2026, 1, 15))));
    List<RevalidationCase> before = List.copyOf(given);

    new RevalqFile().write(given);

    assertThat(given).containsExactlyElementsOf(before);
  }
}
