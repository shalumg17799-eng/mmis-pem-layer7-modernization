import gov.medicaid.pem.fixedwidth.FixedRecord;
import gov.medicaid.pem.fixedwidth.FixedWidthCodec;
import gov.medicaid.pem.fixedwidth.FixedWidthException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * MAM-44 AC-8: every generated dataset parses with libs/fixed-width and round-trips to identical bytes.
 *
 * <p>Reads testdata/manifest.csv; for each .dat file it takes the layout the manifest names, splits the file into
 * records (one per LF line, a CR before it is dropped, as the intake readers do) and, for each record, checks the
 * record has the exact length of the layout, reads it with {@code FixedWidthCodec.read}, reads the fields of its
 * REDEFINES view, writes it back with {@code write} and compares the bytes. Files under negative/ are the
 * opposite check: each record the codec must refuse is listed, and at least one must be refused.
 *
 * <p>Run (no Gradle needed once the library is compiled):
 * java -cp "libs-classes;libs-resources;jackson-databind;jackson-core;jackson-annotations" RoundTrip.java modern/testdata
 */
public final class RoundTrip {

  private RoundTrip() {}

  public static void main(String[] args) throws IOException {
    Path root = Path.of(args.length > 0 ? args[0] : "modern/testdata");
    List<String> manifest = Files.readAllLines(root.resolve("manifest.csv"));
    int failures = 0;
    int files = 0;
    int records = 0;
    for (String line : manifest.subList(1, manifest.size())) {
      String[] c = line.split(",", -1);
      String path = c[0];
      String layout = c[1];
      if (!path.endsWith(".dat")) {
        continue;
      }
      files++;
      FixedWidthCodec codec = FixedWidthCodec.forLayout(layout);
      int length = codec.layout().length();
      boolean negative = path.startsWith("negative/");
      List<byte[]> recs = split(Files.readAllBytes(root.resolve(path)));
      int ok = 0;
      int refused = 0;
      for (int i = 0; i < recs.size(); i++) {
        byte[] rec = recs.get(i);
        int n = i + 1;
        try {
          FixedRecord r = codec.read(rec, n);
          byte[] back = codec.write(r);
          if (rec.length != length || !Arrays.equals(rec, back)) {
            failures++;
            System.out.println("FAIL: " + path + " record " + n + ": the round trip changed the bytes");
          } else {
            readView(r, layout);
            ok++;
          }
        } catch (FixedWidthException e) {
          refused++;
          if (negative) {
            System.out.println("  refused as expected: " + e.getMessage());
          } else {
            failures++;
            System.out.println("FAIL: " + path + ": " + e.getMessage());
          }
        }
      }
      records += recs.size();
      int expected = Integer.parseInt(c[2]);
      boolean count = recs.size() == expected;
      if (!count) {
        failures++;
      }
      if (negative && refused == 0) {
        failures++;
        System.out.println("FAIL: " + path + ": the codec refused no record");
      }
      System.out.println(
          (count && (negative ? refused > 0 : refused == 0) ? "PASS" : "FAIL")
              + ": "
              + path
              + " as "
              + layout
              + " ("
              + length
              + " bytes): "
              + recs.size()
              + " records, "
              + ok
              + " round-tripped identically, "
              + refused
              + " refused"
              + (negative ? " (a negative file)" : ""));
    }
    System.out.println(
        (failures == 0 ? "OK" : "FAIL") + ": " + files + " files, " + records + " records, " + failures + " failure(s)");
    System.exit(failures == 0 ? 0 : 1);
  }

  /** Reads the key fields of the record's REDEFINES view, so a wrong view or position would show here. */
  private static void readView(FixedRecord r, String layout) {
    if (layout.equals("SANCIN")) {
      r.get("SAN-NPI");
      r.get("SAN-ACTION");
      r.getDate("SAN-EFF-DATE");
      return;
    }
    r.get("PRV-NPI");
    String type = r.get("PRV-TYPE-CODE");
    r.getDate("PRV-EFFECTIVE-DATE");
    r.getDate("PRV-REVAL-DUE-DATE");
    if (type.equals("IN")) {
      r.get("PRV-LAST-NAME");
      r.get("PRV-FIRST-NAME");
    } else if (type.equals("GR") || type.equals("FA")) {
      r.get("PRV-LEGAL-NAME");
      r.get("PRV-OWNER-NPI");
    }
  }

  /** One record per LF line; a CR before the LF is dropped; a final empty line is no record. */
  private static List<byte[]> split(byte[] file) {
    List<byte[]> out = new ArrayList<>();
    int start = 0;
    for (int i = 0; i <= file.length; i++) {
      if (i == file.length || file[i] == '\n') {
        int end = i;
        if (end > start && file[end - 1] == '\r') {
          end--;
        }
        if (i < file.length || end > start) {
          out.add(Arrays.copyOfRange(file, start, end));
        }
        start = i + 1;
      }
    }
    return out;
  }
}
