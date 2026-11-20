package gov.medicaid.pem.directory;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Byte-level reading of a DIROUT file (PRVDIR01:DIR-OUT-RECORD,
 * modern/docs/analysis/data-layouts.md): 100-byte records side by side; bytes 1-9 Medicaid ID,
 * 10-19 NPI, 20-21 type, 22 risk tier, 23-82 name, 83-100 spaces.
 */
public final class Dirout {

  public static final int RECORD = 100;

  private Dirout() {}

  /** The file as records of exactly 100 bytes each (one byte per character); fails otherwise. */
  public static List<String> records(byte[] file) {
    assertThat(file.length % RECORD)
        .as("a DIROUT file is a whole number of 100-byte records, but has %s bytes", file.length)
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

  public static String medicaidId(String record) {
    return bytes(record, 1, 9);
  }

  public static String npi(String record) {
    return bytes(record, 10, 19);
  }

  public static String type(String record) {
    return bytes(record, 20, 21);
  }

  public static String tier(String record) {
    return bytes(record, 22, 22);
  }

  public static String name(String record) {
    return bytes(record, 23, 82);
  }

  public static String filler(String record) {
    return bytes(record, 83, 100);
  }

  /** The value padded with spaces on the right to the field length. */
  public static String padded(String value, int length) {
    return value + " ".repeat(length - value.length());
  }
}
