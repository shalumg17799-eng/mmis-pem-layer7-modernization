package gov.medicaid.pem.fixedwidth;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads modern/docs/analysis/data-layouts.md, the reference the exported layouts must agree with
 * (MAM-42 AC-1). The Gradle test task runs in the module folder, so the document is two folders up.
 */
final class DataLayoutsDoc {

  static final Path FILE = Path.of("..", "..", "docs", "analysis", "data-layouts.md");

  private static final Pattern HEADING = Pattern.compile("^###\\s+`([^`]+)`(.*)$");
  private static final Pattern BYTES = Pattern.compile("\\((\\d+) bytes");

  /** One table row with a numeric start and length: a field of a record. */
  record Row(String name, String pic, int start, int length) {
    boolean group() {
      return "(group)".equals(pic);
    }
  }

  /**
   * One "### `OWNER:RECORD`" section; {@code declaredBytes} is the "(N bytes" of its heading, or
   * null.
   */
  record Section(String key, Integer declaredBytes, List<Row> rows) {}

  private DataLayoutsDoc() {}

  static Map<String, Section> read() {
    List<String> lines;
    try {
      lines = Files.readAllLines(FILE);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + FILE.toAbsolutePath(), e);
    }
    Map<String, Section> sections = new LinkedHashMap<>();
    Section current = null;
    for (String line : lines) {
      if (line.startsWith("## ")) {
        current = null;
        continue;
      }
      Matcher heading = HEADING.matcher(line);
      if (heading.matches()) {
        Matcher bytes = BYTES.matcher(heading.group(2));
        Integer declared = bytes.find() ? Integer.valueOf(bytes.group(1)) : null;
        current = new Section(heading.group(1), declared, new ArrayList<>());
        sections.put(current.key(), current);
        continue;
      }
      if (current != null && line.startsWith("|")) {
        Row row = parseRow(line);
        if (row != null) {
          current.rows().add(row);
        }
      }
    }
    return sections;
  }

  /**
   * | Field | Level | PIC / type | Start | Length | Meaning |; header, separator and DB2 "col n"
   * rows give null.
   */
  private static Row parseRow(String line) {
    String[] cells = line.split("\\|", -1);
    if (cells.length < 6) {
      return null;
    }
    try {
      String name = cells[1].replace("&nbsp;", "").trim();
      return new Row(
          name,
          cells[3].trim(),
          Integer.parseInt(cells[4].trim()),
          Integer.parseInt(cells[5].trim()));
    } catch (NumberFormatException notAFieldRow) {
      return null;
    }
  }
}
