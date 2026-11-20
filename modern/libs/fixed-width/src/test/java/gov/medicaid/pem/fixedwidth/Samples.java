package gov.medicaid.pem.fixedwidth;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Synthetic legacy-format records for the codec tests (MAM-42). Every value is fake: obviously test
 * names, the NPIs of aws-transform/test-data and the business date 2026-01-15. The builders check
 * their own lengths, so a typo here fails loudly instead of masking a codec defect.
 */
final class Samples {

  /** One byte per character; EBCDIC conversion is out of scope (dat-ebcdic). */
  static final Charset LEGACY_BYTES = StandardCharsets.ISO_8859_1;

  /** A named sample record for the round-trip tests. */
  record Sample(String layout, String label, byte[] bytes) {
    @Override
    public String toString() {
      return layout + " " + label;
    }
  }

  // ---- PRVRECRD (130 bytes): the REDEFINES variant by provider type

  static final String IN_VARIANT =
      pad("TESTPROV", 30) + pad("ALPHA", 20) + pad("MD12345", 15) + "TX" + pad("", 13);
  static final String GR_VARIANT =
      pad("SAMPLE CLINIC 01", 40) + pad("SAMPLE CLINIC DBA", 30) + "1990000111";
  static final String FA_VARIANT = pad("SAMPLE HOSPITAL 01", 40) + pad("", 30) + "1990000999";

  static final String IN_MASTER =
      master(
          "1003000111",
          "TXM000111",
          "IN",
          "207Q00000X",
          "L",
          "A",
          "P",
          "20260115",
          "20310115",
          IN_VARIANT);
  static final String GR_MASTER =
      master(
          "1003000444",
          "TXM000444",
          "GR",
          "193200000X",
          "H",
          "P",
          "U",
          "20260115",
          "20310115",
          GR_VARIANT);
  static final String FA_MASTER =
      master(
          "1003000777",
          "TXM000777",
          "FA",
          "282N00000X",
          "M",
          "A",
          "P",
          "20260115",
          "20310115",
          FA_VARIANT);

  /** APPINPUT bytes 131-200: reserved (D-025). */
  static final String RESERVED = pad("", 70);

  // ---- the other legacy files

  static final String SCREEN_PASS =
      checked(
          67,
          pad("1003000111", 10)
              + pad("OIGLEIE", 8)
              + "P"
              + "20260115"
              + pad("NO MATCH ON EXCLUSION LIST", 40));
  static final String SCREEN_FAIL =
      checked(
          67,
          pad("1003000555", 10)
              + pad("LICBOARD", 8)
              + "F"
              + "20260115"
              + pad("LICENSE EXPIRED", 40));
  static final String SANCTION_TERMINATE = sanction("T");
  static final String SANCTION_SUSPEND = sanction("S");
  static final String SANCTION_REINSTATE = sanction("R");
  static final String REVAL =
      checked(80, pad("1003000111", 10) + "20260115" + pad("RESCREEN-REQUIRED", 20) + pad("", 42));
  static final String TMSIS =
      checked(120, "PRV01" + pad("TXM000111", 9) + "1003000111" + "A" + "26015" + pad("", 90));
  static final String DIRECTORY =
      checked(
          100,
          pad("TXM000111", 9)
              + "1003000111"
              + "IN"
              + "L"
              + pad("TESTPROV ALPHA", 60)
              + pad("", 18));
  static final String APP_ERROR =
      checked(250, pad("1003000555 TYPE:IN ERR:VALIDATION-FAILED", 250));

  private Samples() {}

  /**
   * Every sample the round-trip test covers: at least one per layout, an IN and a GR record
   * included.
   */
  static List<Sample> roundTripSamples() {
    return List.of(
        new Sample("PRVRECRD", "IN", bytes(IN_MASTER)),
        new Sample("PRVRECRD", "GR", bytes(GR_MASTER)),
        new Sample("PRVRECRD", "FA", bytes(FA_MASTER)),
        new Sample("APPINPUT", "IN", bytes(IN_MASTER + RESERVED)),
        new Sample("APPINPUT", "GR", bytes(GR_MASTER + RESERVED)),
        new Sample("APPINPUT", "FA", bytes(FA_MASTER + RESERVED)),
        new Sample("SCRNREC", "pass", bytes(SCREEN_PASS)),
        new Sample("SCRNREC", "fail", bytes(SCREEN_FAIL)),
        new Sample("SANCIN", "terminate", bytes(SANCTION_TERMINATE)),
        new Sample("SANCIN", "suspend", bytes(SANCTION_SUSPEND)),
        new Sample("SANCIN", "reinstate", bytes(SANCTION_REINSTATE)),
        new Sample("REVALQ", "rescreen", bytes(REVAL)),
        new Sample("TMSISOUT", "active", bytes(TMSIS)),
        new Sample("DIROUT", "individual", bytes(DIRECTORY)),
        new Sample("APPERROR", "validation", bytes(APP_ERROR)));
  }

  // ---- helpers

  /** Right-pads with spaces; refuses to truncate. */
  static String pad(String value, int length) {
    if (value.length() > length) {
      throw new IllegalArgumentException("'" + value + "' is longer than " + length);
    }
    return value + " ".repeat(length - value.length());
  }

  static byte[] bytes(String record) {
    return record.getBytes(LEGACY_BYTES);
  }

  /** The text of {@code length} bytes starting at the 1-based byte {@code start}. */
  static String slice(byte[] record, int start, int length) {
    return new String(record, start - 1, length, LEGACY_BYTES);
  }

  /** A copy of the record with the bytes from the 1-based {@code start} replaced. */
  static byte[] overwrite(String record, int start, String replacement) {
    byte[] copy = bytes(record);
    byte[] with = bytes(replacement);
    System.arraycopy(with, 0, copy, start - 1, with.length);
    return copy;
  }

  static byte[] spaces(int length) {
    byte[] out = new byte[length];
    Arrays.fill(out, (byte) ' ');
    return out;
  }

  /**
   * The IN sample, built field by field (works for PRVRECRD and APPINPUT, which share the field
   * names).
   */
  static FixedRecord individualRecord(FixedWidthCodec codec) {
    FixedRecord r = codec.newRecord();
    r.set("PRV-NPI", "1003000111");
    r.set("PRV-MEDICAID-ID", "TXM000111");
    r.set("PRV-TYPE-CODE", "IN");
    r.set("PRV-TAXONOMY", "207Q00000X");
    r.set("PRV-RISK-TIER", "L");
    r.set("PRV-ENROLL-STATUS", "A");
    r.set("PRV-SCREEN-FLAG", "P");
    r.setDate("PRV-EFFECTIVE-DATE", LocalDate.of(2026, 1, 15));
    r.setDate("PRV-REVAL-DUE-DATE", LocalDate.of(2031, 1, 15));
    r.set("PRV-LAST-NAME", "TESTPROV");
    r.set("PRV-FIRST-NAME", "ALPHA");
    r.set("PRV-LICENSE-NO", "MD12345");
    r.set("PRV-LICENSE-STATE", "TX");
    return r;
  }

  /** The GR sample, built field by field. */
  static FixedRecord groupRecord(FixedWidthCodec codec) {
    FixedRecord r = codec.newRecord();
    r.set("PRV-NPI", "1003000444");
    r.set("PRV-MEDICAID-ID", "TXM000444");
    r.set("PRV-TYPE-CODE", "GR");
    r.set("PRV-TAXONOMY", "193200000X");
    r.set("PRV-RISK-TIER", "H");
    r.set("PRV-ENROLL-STATUS", "P");
    r.set("PRV-SCREEN-FLAG", "U");
    r.setDate("PRV-EFFECTIVE-DATE", LocalDate.of(2026, 1, 15));
    r.setDate("PRV-REVAL-DUE-DATE", LocalDate.of(2031, 1, 15));
    r.set("PRV-LEGAL-NAME", "SAMPLE CLINIC 01");
    r.set("PRV-DBA-NAME", "SAMPLE CLINIC DBA");
    r.set("PRV-OWNER-NPI", "1990000111");
    return r;
  }

  private static String master(
      String npi,
      String medicaidId,
      String type,
      String taxonomy,
      String riskTier,
      String status,
      String screenFlag,
      String effectiveDate,
      String revalDueDate,
      String variant) {
    return checked(
        130,
        pad(npi, 10)
            + pad(medicaidId, 9)
            + pad(type, 2)
            + pad(taxonomy, 10)
            + riskTier
            + status
            + screenFlag
            + effectiveDate
            + revalDueDate
            + variant);
  }

  private static String sanction(String action) {
    return checked(
        80,
        pad("1003000444", 10)
            + action
            + pad("SAMPLE REASON: OWNER EXCLUDED", 40)
            + "20260115"
            + pad("", 21));
  }

  private static String checked(int length, String record) {
    if (record.length() != length) {
      throw new IllegalStateException("sample is " + record.length() + " bytes, not " + length);
    }
    return record;
  }
}
