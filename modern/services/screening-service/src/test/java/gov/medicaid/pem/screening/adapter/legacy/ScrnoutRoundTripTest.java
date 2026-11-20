package gov.medicaid.pem.screening.adapter.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.fixedwidth.FixedRecord;
import gov.medicaid.pem.fixedwidth.FixedWidthCodec;
import gov.medicaid.pem.screening.application.ScreeningRuns.Screened;
import gov.medicaid.pem.screening.domain.Source;
import gov.medicaid.pem.screening.domain.SourceResult;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MAM-32 AC-3: the round trip of the legacy adapter (cobol-to-java 7). The SCRNOUT file the writer
 * produces is read record by record with the codec of the SCRNREC layout (which validates it) and
 * written again: the bytes are the same, and the fields the codec reads are what was screened. The
 * layout is 67 bytes. There is no golden SCRNOUT file of a legacy run yet (the parity phase
 * produces one), so no golden-file test exists.
 */
class ScrnoutRoundTripTest {

  static final int LENGTH = 67;
  static final Instant AT = Instant.parse("2026-03-31T10:00:00Z");
  static final LocalDate DATE = LocalDate.of(2026, 3, 31);

  final ScrnoutWriter writer = new ScrnoutWriter();
  final FixedWidthCodec codec = FixedWidthCodec.forLayout("SCRNREC");

  static SourceResult r(Source source, String result, String detail, String code) {
    return new SourceResult(source, result, AT, detail, code);
  }

  /** 1003000111 passes everything; 1003000555 has an expired licence and an unanswered LEIE. */
  static List<Screened> screened() {
    return List.of(
        new Screened(
            "1003000111",
            List.of(
                r(Source.LICBOARD, "P", "LICENCE MD12345 TX ACTIVE UNTIL 2028-09", null),
                r(Source.OIGLEIE, "P", "NOT EXCLUDED", null),
                r(Source.SAMGOV, "P", "NOT DEBARRED", null),
                r(Source.SSADMF, "P", "NOT DECEASED", null))),
        new Screened(
            "1003000555",
            List.of(
                r(Source.LICBOARD, "F", "LICENCE PT99900 TX EXPIRED 2024-01", "E202"),
                r(Source.OIGLEIE, "C", "COULD NOT BE REACHED", null),
                r(Source.SAMGOV, "P", "NOT DEBARRED", null),
                r(Source.SSADMF, "P", "NOT DECEASED", null))));
  }

  /** The records of the file, each 67 bytes. */
  static List<byte[]> records(byte[] file) {
    assertThat(file.length % LENGTH).isZero();
    List<byte[]> out = new ArrayList<>();
    for (int at = 0; at < file.length; at += LENGTH) {
      out.add(Arrays.copyOfRange(file, at, at + LENGTH));
    }
    return out;
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-3] the SCRNREC layout of the codec is 67 bytes, the length of every record")
  void layoutIs67Bytes() {
    assertThat(codec.layout().length()).isEqualTo(LENGTH);
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-3] the codec reads every record of the file and finds NPI, source, result, verdict date and detail as screened")
  void codecReadsTheFile() {
    List<byte[]> records = records(writer.write(screened(), DATE));
    assertThat(records).hasSize(8);
    List<String> expected = new ArrayList<>();
    for (Screened s : screened()) {
      for (SourceResult x : s.results()) {
        expected.add(s.npi() + "|" + x.source() + "|" + x.result() + "|" + x.detail());
      }
    }
    List<String> actual = new ArrayList<>();
    for (int i = 0; i < records.size(); i++) {
      FixedRecord rec = codec.read(records.get(i), i + 1);
      assertThat(rec.getDate("SCR-VERDICT-DATE")).isEqualTo(DATE);
      actual.add(
          rec.get("SCR-NPI")
              + "|"
              + rec.get("SCR-SOURCE")
              + "|"
              + rec.get("SCR-RESULT")
              + "|"
              + rec.get("SCR-DETAIL"));
    }
    assertThat(actual).containsExactlyElementsOf(expected);
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-3] round trip: each record read by the codec and written again is the same 67 bytes")
  void everyRecordRoundTrips() {
    List<byte[]> records = records(writer.write(screened(), DATE));
    for (int i = 0; i < records.size(); i++) {
      FixedRecord rec = codec.read(records.get(i), i + 1);
      assertThat(codec.write(rec)).as("record %d", i + 1).containsExactly(records.get(i));
    }
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-3] round trip of the whole file: the records read and written again are the same file")
  void wholeFileRoundTrips() {
    byte[] file = writer.write(screened(), DATE);
    ByteArrayOutputStream again = new ByteArrayOutputStream();
    List<byte[]> records = records(file);
    for (int i = 0; i < records.size(); i++) {
      again.writeBytes(codec.write(codec.read(records.get(i), i + 1)));
    }
    assertThat(again.toByteArray()).containsExactly(file);
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-3] a record whose detail is cut to 40 characters still round-trips, with the cut text")
  void cutDetailRoundTrips() {
    String detail = "OWNER 1990000111 EXCLUDED 1128(a)(1) SINCE 2023-01-15";
    byte[] file =
        writer.write(
            List.of(
                new Screened(
                    "1003000444",
                    List.of(
                        r(Source.OIGLEIE, "F", detail, "E203"),
                        r(Source.LICBOARD, "P", "NOT APPLICABLE", null),
                        r(Source.SAMGOV, "P", "NOT DEBARRED", null),
                        r(Source.SSADMF, "P", "NOT DECEASED", null)))),
            DATE);
    List<byte[]> records = records(file);
    FixedRecord oigleie = codec.read(records.get(1), 2);
    assertThat(oigleie.get("SCR-SOURCE")).isEqualTo("OIGLEIE");
    assertThat(oigleie.get("SCR-DETAIL")).isEqualTo(detail.substring(0, 40));
    assertThat(codec.write(oigleie)).containsExactly(records.get(1));
  }
}
