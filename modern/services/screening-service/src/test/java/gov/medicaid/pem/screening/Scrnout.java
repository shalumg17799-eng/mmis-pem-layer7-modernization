package gov.medicaid.pem.screening;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The legacy SCRNOUT file (SCRNREC, 67 bytes, modern/docs/analysis/data-layouts.md): NPI bytes
 * 1-10, SOURCE 11-18, RESULT 19, VERDICT-DATE 20-27 (CCYYMMDD) and DETAIL 28-67. The tests read the
 * file by byte position, never through the codec of the service.
 */
public final class Scrnout {

  public static final int LENGTH = 67;

  private Scrnout() {}

  /** One record, by byte position. */
  public record Row(String npi, String source, String result, String date, String detail) {

    public String text() {
      return npi + source + result + date + detail;
    }
  }

  /** The record the legacy layout has for these values: source and detail left-justified. */
  public static String record(
      String npi, String source, String result, String verdictDate, String detail) {
    String d = detail.length() <= 40 ? detail : detail.substring(0, 40);
    String r = String.format("%-10s%-8s%s%s%-40s", npi, source, result, verdictDate, d);
    assertThat(r).hasSize(LENGTH);
    return r;
  }

  /** The records of the file; the file must be a whole number of 67-byte records. */
  public static List<Row> rows(byte[] file) {
    assertThat(file.length % LENGTH).as("file length %d is a multiple of 67", file.length).isZero();
    List<Row> rows = new ArrayList<>();
    for (int at = 0; at < file.length; at += LENGTH) {
      String r = new String(file, at, LENGTH, StandardCharsets.ISO_8859_1);
      rows.add(
          new Row(
              r.substring(0, 10),
              r.substring(10, 18),
              r.substring(18, 19),
              r.substring(19, 27),
              r.substring(27, 67)));
    }
    return rows;
  }

  /** The records of the file as 67-character texts. */
  public static List<String> records(byte[] file) {
    return rows(file).stream().map(Row::text).toList();
  }

  /** The text of the file: no line ends, only printable ASCII. */
  public static void assertFixedLengthAscii(byte[] file) {
    for (byte b : file) {
      assertThat(b).as("every byte is printable ASCII").isBetween((byte) 0x20, (byte) 0x7E);
    }
  }
}
