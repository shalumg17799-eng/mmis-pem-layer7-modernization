package gov.medicaid.pem.enrollment;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Synthetic APPINPUT files for the MAM-56 and MAM-57 tests. A record has 200 bytes: PRVRECRD in
 * bytes 1-130 as PRVENR01 reads it (NPI 1-10, Medicaid ID 11-19, type 20-21, taxonomy 22-31, risk
 * tier 32, status 33, screen flag 34, effective date 35-42, revalidation due date 43-50 and the
 * variant 51-130: individual last name 51-80, first name 81-100, licence number 101-115, licence
 * state 116-117; organisation legal name 51-90, DBA name 91-120, owner NPI 121-130) and the
 * reserved bytes 131-200 (D-025). Never real data.
 */
public final class AppInputFile {

  /** The record length of the APPINPUT file. */
  public static final int LRECL = 200;

  /** The record length of the APPERROR file. */
  public static final int ERROR_LRECL = 250;

  private AppInputFile() {}

  /** An individual application (type IN unless another type code is given). */
  public static String individual(
      String npi,
      String medicaidId,
      String type,
      String taxonomy,
      String lastName,
      String firstName,
      String licenseNo,
      String licenseState) {
    char[] r = blank();
    put(r, 1, 10, "npi", npi);
    put(r, 11, 9, "medicaid id", medicaidId);
    put(r, 20, 2, "type", type);
    put(r, 22, 10, "taxonomy", taxonomy);
    put(r, 51, 30, "last name", lastName);
    put(r, 81, 20, "first name", firstName);
    put(r, 101, 15, "licence number", licenseNo);
    put(r, 116, 2, "licence state", licenseState);
    return new String(r);
  }

  /** An organisation application (type GR or FA). */
  public static String organization(
      String npi,
      String medicaidId,
      String type,
      String taxonomy,
      String legalName,
      String dbaName,
      String ownerNpi) {
    char[] r = blank();
    put(r, 1, 10, "npi", npi);
    put(r, 11, 9, "medicaid id", medicaidId);
    put(r, 20, 2, "type", type);
    put(r, 22, 10, "taxonomy", taxonomy);
    put(r, 51, 40, "legal name", legalName);
    put(r, 91, 30, "dba name", dbaName);
    put(r, 121, 10, "owner npi", ownerNpi);
    return new String(r);
  }

  /** A valid individual application for the NPI: type IN, taxonomy 207Q00000X, RIVERA MARIA. */
  public static String valid(String npi) {
    return individual(npi, "M00000" + npi.substring(7), "IN", "207Q00000X", "RIVERA", "MARIA", "MD12345", "TX");
  }

  /** A valid individual application whose taxonomy is empty (E104). */
  public static String noTaxonomy(String npi) {
    return individual(npi, "M00000" + npi.substring(7), "IN", "", "RIVERA", "MARIA", "MD12345", "TX");
  }

  /** A valid individual application with another type code (E102 when the code is unknown). */
  public static String ofType(String npi, String type) {
    return individual(npi, "M00000" + npi.substring(7), type, "207Q00000X", "RIVERA", "MARIA", "MD12345", "TX");
  }

  /** A valid individual application with an empty NPI (E101). */
  public static String noNpi() {
    return individual("", "M00000000", "IN", "207Q00000X", "RIVERA", "MARIA", "MD12345", "TX");
  }

  /** The record with {@code text} written from byte {@code start} (1-based) on. */
  public static String with(String record, int start, String text) {
    char[] r = record.toCharArray();
    for (int i = 0; i < text.length(); i++) {
      r[start - 1 + i] = text.charAt(i);
    }
    return new String(r);
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

  /** Records of 200 bytes one after the other, with no line ends (a fixed-block mainframe file). */
  public static byte[] blocks(String... records) {
    return join("", false, records);
  }

  /** One record per line, LF between the lines and none after the last. */
  public static byte[] linesWithoutFinalEnd(String... records) {
    return join("\n", false, records);
  }

  /** The expected APPERROR row for an NPI and a type code as read: 250 characters. */
  public static String errorRow(String npi, String type) {
    String row = String.format("%-10s TYPE:%-2s ERR:VALIDATION-FAILED", npi, type);
    return row + " ".repeat(ERROR_LRECL - row.length());
  }

  /** The rows of an APPERROR file: 250 characters each; any line end between rows is ignored. */
  public static List<String> rows(String errorFile) {
    String flat = errorFile.replace("\r", "").replace("\n", "");
    List<String> rows = new ArrayList<>();
    for (int i = 0; i < flat.length(); i += ERROR_LRECL) {
      rows.add(flat.substring(i, Math.min(flat.length(), i + ERROR_LRECL)));
    }
    return rows;
  }

  /** A synthetic dataset of modern/testdata, for example {@code fixed-width/appinput-baseline.dat}. */
  public static byte[] dataset(String name) throws IOException {
    Path rel = Path.of("testdata", name);
    Path dir = Path.of("").toAbsolutePath();
    while (dir != null) {
      for (Path c : List.of(dir.resolve(rel), dir.resolve("modern").resolve(rel))) {
        if (Files.isRegularFile(c)) {
          return Files.readAllBytes(c);
        }
      }
      dir = dir.getParent();
    }
    throw new IllegalStateException("synthetic dataset not found: " + rel);
  }

  /** The 200-byte records of a dataset file, one per line (the line ends are not part of them). */
  public static List<String> datasetRecords(String name) throws IOException {
    String text = new String(dataset(name), StandardCharsets.ISO_8859_1);
    List<String> records = new ArrayList<>();
    for (String line : text.split("\n", -1)) {
      if (!line.isEmpty()) {
        records.add(line.replace("\r", ""));
      }
    }
    return records;
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

  private static char[] blank() {
    char[] r = new char[LRECL];
    java.util.Arrays.fill(r, ' ');
    return r;
  }

  private static void put(char[] record, int start, int length, String name, String value) {
    if (value == null || value.length() > length) {
      throw new IllegalArgumentException(name + " must be at most " + length + " characters");
    }
    for (int i = 0; i < value.length(); i++) {
      record[start - 1 + i] = value.charAt(i);
    }
  }
}
