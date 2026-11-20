package gov.medicaid.pem.sanctions;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Synthetic SANCIN files for the MAM-55 tests: records of 80 bytes as PRVSAN01's SANCTION-IN-RECORD
 * lays them out (NPI 1-10, action 11, reason 12-51, effective date CCYYMMDD 52-59, filler 60-80).
 * Never real data.
 */
public final class SancinFile {

  /** The record length of the SANCIN file. */
  public static final int LRECL = 80;

  private SancinFile() {}

  /** One record of exactly 80 bytes; the values are padded on the right with spaces. */
  public static String record(String npi, String action, String reason, String ccyymmdd) {
    require("npi", npi, 10);
    require("action", action, 1);
    require("reason", reason, 40);
    require("effective date", ccyymmdd, 8);
    return String.format("%-10s%-1s%-40s%-8s%21s", npi, action, reason, ccyymmdd, "");
  }

  /** The record cut to its first {@code bytes} bytes: an unreadable (short) record. */
  public static String cut(String record, int bytes) {
    return record.substring(0, bytes);
  }

  /** One record per line, every line ended by LF (the way the synthetic data files are written). */
  public static byte[] lines(String... records) {
    return join("\n", true, records);
  }

  /** One record per line, every line ended by CR LF. */
  public static byte[] crlf(String... records) {
    return join("\r\n", true, records);
  }

  /** Records of 80 bytes one after the other, with no line ends (a fixed-block mainframe file). */
  public static byte[] blocks(String... records) {
    return join("", false, records);
  }

  private static byte[] join(String lineEnd, boolean last, String[] records) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (int i = 0; i < records.length; i++) {
      out.writeBytes(records[i].getBytes(StandardCharsets.ISO_8859_1));
      if (last || i < records.length - 1) {
        out.writeBytes(lineEnd.getBytes(StandardCharsets.ISO_8859_1));
      }
    }
    return out.toByteArray();
  }

  private static void require(String name, String value, int max) {
    if (value == null || value.length() > max) {
      throw new IllegalArgumentException(name + " must be at most " + max + " characters");
    }
  }
}
