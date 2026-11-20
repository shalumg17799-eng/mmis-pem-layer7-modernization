package gov.medicaid.pem.screening.adapter.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.screening.Scrnout;
import gov.medicaid.pem.screening.application.ScreeningRuns.Screened;
import gov.medicaid.pem.screening.domain.Source;
import gov.medicaid.pem.screening.domain.SourceResult;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * MAM-32 AC-3 and AC-4, byte by byte: the SCRNOUT file of the legacy layout SCRNREC (67 bytes:
 * NPI 1-10, SOURCE 11-18, RESULT 19, VERDICT-DATE 20-27, DETAIL 28-67; ADR-005, question C-7 A),
 * four rows per screened provider in ascending NPI order and the order LICBOARD, OIGLEIE, SAMGOV,
 * SSADMF. The file is read by byte position, never with the codec that wrote it.
 */
class ScrnoutWriterTest {

  static final Instant AT = Instant.parse("2026-03-31T10:00:00Z");
  static final LocalDate DATE = LocalDate.of(2026, 3, 31);

  final ScrnoutWriter writer = new ScrnoutWriter();

  static SourceResult r(Source source, String result, String detail) {
    return new SourceResult(source, result, AT, detail, null);
  }

  static List<SourceResult> allPass() {
    return List.of(
        r(Source.LICBOARD, "P", "LICENCE MD12345 TX ACTIVE UNTIL 2028-09"),
        r(Source.OIGLEIE, "P", "NOT EXCLUDED"),
        r(Source.SAMGOV, "P", "NOT DEBARRED"),
        r(Source.SSADMF, "P", "NOT DECEASED"));
  }

  static String text(byte[] file) {
    return new String(file, StandardCharsets.ISO_8859_1);
  }

  @Test
  @DisplayName("[MAM-32 AC-3] one provider gives four records of 67 bytes, as the legacy layout has them")
  void oneProviderExactBytes() {
    byte[] file = writer.write(List.of(new Screened("1003000111", allPass())), DATE);
    assertThat(file).hasSize(4 * 67);
    assertThat(text(file))
        .isEqualTo(
            "1003000111" + "LICBOARD" + "P" + "20260331" + pad("LICENCE MD12345 TX ACTIVE UNTIL 2028-09")
                + "1003000111" + "OIGLEIE " + "P" + "20260331" + pad("NOT EXCLUDED")
                + "1003000111" + "SAMGOV  " + "P" + "20260331" + pad("NOT DEBARRED")
                + "1003000111" + "SSADMF  " + "P" + "20260331" + pad("NOT DECEASED"));
  }

  /** A detail as the legacy field holds it: left-justified, padded with spaces to 40 bytes. */
  static String pad(String detail) {
    return detail + " ".repeat(40 - detail.length());
  }

  @Test
  @DisplayName("[MAM-32 AC-3] the records have no line separator and hold printable ASCII only")
  void noLineEnds() {
    byte[] file = writer.write(List.of(new Screened("1003000111", allPass())), DATE);
    Scrnout.assertFixedLengthAscii(file);
    assertThat(text(file)).doesNotContain("\n").doesNotContain("\r");
  }

  @Test
  @DisplayName("[MAM-32 AC-3] providers come in ascending NPI order whatever the order given")
  void providersAscending() {
    List<Screened> given =
        List.of(
            new Screened("1003000555", allPass()),
            new Screened("1003000111", allPass()),
            new Screened("1003000333", allPass()));
    List<String> npis = Scrnout.rows(writer.write(given, DATE)).stream().map(Scrnout.Row::npi).toList();
    assertThat(npis)
        .containsExactly(
            "1003000111", "1003000111", "1003000111", "1003000111",
            "1003000333", "1003000333", "1003000333", "1003000333",
            "1003000555", "1003000555", "1003000555", "1003000555");
  }

  @Test
  @DisplayName("[MAM-32 AC-3] the four rows of a provider are LICBOARD, OIGLEIE, SAMGOV, SSADMF whatever the order given")
  void sourcesInLegacyOrder() {
    List<SourceResult> shuffled = new ArrayList<>(allPass());
    Collections.reverse(shuffled);
    List<String> sources =
        Scrnout.rows(writer.write(List.of(new Screened("1003000111", shuffled)), DATE)).stream()
            .map(row -> row.source().trim())
            .toList();
    assertThat(sources).containsExactly("LICBOARD", "OIGLEIE", "SAMGOV", "SSADMF");
  }

  @ParameterizedTest(name = "[MAM-32 AC-3] source {0} is written left-justified in 8 bytes: <{1}>")
  @CsvSource({"LICBOARD,'LICBOARD'", "OIGLEIE,'OIGLEIE '", "SAMGOV,'SAMGOV  '", "SSADMF,'SSADMF  '"})
  void sourceLeftJustified(String source, String expected) {
    byte[] file = writer.write(List.of(new Screened("1003000111", allPass())), DATE);
    String record =
        Scrnout.rows(file).stream()
            .filter(row -> row.source().trim().equals(source))
            .findFirst()
            .orElseThrow()
            .text();
    assertThat(record.substring(10, 18)).isEqualTo(expected);
  }

  @ParameterizedTest(name = "[MAM-32 AC-3] the business date {0} is the verdict date {1} in bytes 20-27")
  @CsvSource({"2026-03-31,20260331", "2026-01-05,20260105", "2024-02-29,20240229", "2099-12-31,20991231"})
  void verdictDateIsTheBusinessDate(LocalDate date, String expected) {
    List<Scrnout.Row> rows =
        Scrnout.rows(writer.write(List.of(new Screened("1003000111", allPass())), date));
    assertThat(rows).allSatisfy(row -> assertThat(row.date()).isEqualTo(expected));
  }

  @ParameterizedTest(name = "[MAM-32 AC-4] result {0} is written as it is in byte 19")
  @CsvSource({"P", "F", "C"})
  void resultByte(String result) {
    List<SourceResult> results =
        List.of(
            r(Source.LICBOARD, result, "X"),
            r(Source.OIGLEIE, "P", "Y"),
            r(Source.SAMGOV, "P", "Y"),
            r(Source.SSADMF, "P", "Y"));
    List<Scrnout.Row> rows =
        Scrnout.rows(writer.write(List.of(new Screened("1003000555", results)), DATE));
    assertThat(rows.get(0).source().trim()).isEqualTo("LICBOARD");
    assertThat(rows.get(0).result()).isEqualTo(result);
    assertThat(rows.get(1).result()).isEqualTo("P");
  }

  @Test
  @DisplayName("[MAM-32 AC-4] the error code of a result is not written: the legacy record has no field for it")
  void errorCodeNotWritten() {
    SourceResult expired =
        new SourceResult(Source.LICBOARD, "F", AT, "LICENCE PT99900 TX EXPIRED 2024-01", "E202");
    List<SourceResult> results = new ArrayList<>(allPass());
    results.set(0, expired);
    String file = text(writer.write(List.of(new Screened("1003000555", results)), DATE));
    assertThat(file).doesNotContain("E202");
    assertThat(file)
        .startsWith(
            "1003000555" + "LICBOARD" + "F" + "20260331" + pad("LICENCE PT99900 TX EXPIRED 2024-01"));
  }

  @Test
  @DisplayName("[MAM-32 AC-3] a detail of 40 characters is kept whole, one of 41 is cut to 40")
  void detailCutAt40() {
    String forty = "A".repeat(40);
    List<SourceResult> results =
        List.of(
            r(Source.LICBOARD, "F", forty),
            r(Source.OIGLEIE, "F", forty + "B"),
            r(Source.SAMGOV, "F", "A".repeat(39)),
            r(Source.SSADMF, "F", "C".repeat(60)));
    List<Scrnout.Row> rows =
        Scrnout.rows(writer.write(List.of(new Screened("1003000111", results)), DATE));
    assertThat(rows.get(0).detail()).isEqualTo(forty);
    assertThat(rows.get(1).detail()).isEqualTo(forty);
    assertThat(rows.get(2).detail()).isEqualTo("A".repeat(39) + " ");
    assertThat(rows.get(3).detail()).isEqualTo("C".repeat(40));
  }

  @Test
  @DisplayName("[MAM-32 AC-3] a missing detail is written as 40 spaces")
  void nullDetail() {
    List<SourceResult> results =
        List.of(
            new SourceResult(Source.LICBOARD, "P", AT, null, null),
            r(Source.OIGLEIE, "P", ""),
            r(Source.SAMGOV, "P", "Y"),
            r(Source.SSADMF, "P", "Y"));
    List<Scrnout.Row> rows =
        Scrnout.rows(writer.write(List.of(new Screened("1003000111", results)), DATE));
    assertThat(rows.get(0).detail()).isEqualTo(" ".repeat(40));
    assertThat(rows.get(1).detail()).isEqualTo(" ".repeat(40));
  }

  @Test
  @DisplayName("[MAM-32 AC-7] no screened provider gives an empty file")
  void emptyFile() {
    assertThat(writer.write(List.of(), DATE)).isEmpty();
  }

  @Test
  @DisplayName("[MAM-32 AC-3] two providers give eight records: the second follows the first without a gap")
  void twoProviders() {
    byte[] file =
        writer.write(
            List.of(
                new Screened("1003000555", allPass()), new Screened("1003000111", allPass())),
            DATE);
    assertThat(file).hasSize(8 * 67);
    assertThat(Scrnout.records(file).get(0)).startsWith("1003000111LICBOARDP");
    assertThat(Scrnout.records(file).get(4)).startsWith("1003000555LICBOARDP");
  }
}
