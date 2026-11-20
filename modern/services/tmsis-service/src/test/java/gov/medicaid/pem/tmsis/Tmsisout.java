package gov.medicaid.pem.tmsis;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Byte-level reading of a TMSISOUT file (PRVTMS01:WS-TMSIS-DETAIL,
 * modern/docs/analysis/data-layouts.md): 120-byte records side by side; bytes 1-5 the record type
 * PRV01, 6-14 the Medicaid ID, 15-24 the NPI, 25 the status, 26-30 the YYDDD period, 31-120 spaces.
 */
public final class Tmsisout {

  public static final int RECORD = 120;

  private Tmsisout() {}

  /** The file as records of exactly 120 bytes each (one byte per character); fails otherwise. */
  public static List<String> records(byte[] file) {
    assertThat(file.length % RECORD)
        .as("a TMSISOUT file is a whole number of 120-byte records, but has %s bytes", file.length)
        .isZero();
    String all = new String(file, StandardCharsets.ISO_8859_1);
    List<String> out = new ArrayList<>();
    for (int i = 0; i < all.length(); i += RECORD) {
      out.add(all.substring(i, i + RECORD));
    }
    return out;
  }

  /** Bytes from..to of one record, 1-based and inclusive, as in the layout document. */
  public static String bytes(String record, int from, int to) {
    return record.substring(from - 1, to);
  }

  public static String recordType(String record) {
    return bytes(record, 1, 5);
  }

  public static String medicaidId(String record) {
    return bytes(record, 6, 14);
  }

  public static String npi(String record) {
    return bytes(record, 15, 24);
  }

  public static String status(String record) {
    return bytes(record, 25, 25);
  }

  public static String period(String record) {
    return bytes(record, 26, 30);
  }

  public static String filler(String record) {
    return bytes(record, 31, 120);
  }

  /** The NPIs of the records of the file, in file order. */
  public static List<String> npis(byte[] file) {
    return records(file).stream().map(Tmsisout::npi).toList();
  }

  /** The whole record the legacy program writes for this provider and period. */
  public static String expected(String medicaidId, String npi, String period) {
    return "PRV01" + medicaidId + npi + "A" + period + " ".repeat(90);
  }
}
