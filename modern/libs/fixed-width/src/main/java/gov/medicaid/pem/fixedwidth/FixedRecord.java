package gov.medicaid.pem.fixedwidth;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Year;
import java.util.Arrays;

/**
 * One record of a layout, held as its bytes (ISO-8859-1, one byte per character). Fields of a
 * REDEFINES view exist only when the discriminator holds one of the view's values; anything else is
 * an explicit error, never a silent reinterpretation, truncation or default (D-024).
 */
public final class FixedRecord {

  private final RecordLayout layout;
  private final byte[] bytes;
  private final long recordNumber;

  FixedRecord(RecordLayout layout, byte[] bytes, long recordNumber) {
    this.layout = layout;
    this.bytes = bytes;
    this.recordNumber = recordNumber;
  }

  public RecordLayout layout() {
    return layout;
  }

  byte[] bytes() {
    return Arrays.copyOf(bytes, bytes.length);
  }

  /** An alphanumeric value without its trailing spaces; a numeric or date value as its digits. */
  public String get(String field) {
    FieldDef f = present(field);
    String raw = raw(f);
    if (f.type() != FieldType.ALPHANUMERIC) {
      return raw;
    }
    int end = raw.length();
    while (end > 0 && raw.charAt(end - 1) == ' ') {
      end--;
    }
    return raw.substring(0, end);
  }

  /**
   * An alphanumeric value is right-padded with spaces; a numeric or date field takes digits only,
   * left zero-filled. A value longer than the field is an error.
   */
  public void set(String field, String value) {
    FieldDef f = present(field);
    String v = value == null ? "" : value;
    if (v.length() > f.length()) {
      throw error(f, "value of " + v.length() + " characters does not fit in " + f.length());
    }
    if (f.type() == FieldType.ALPHANUMERIC) {
      put(f, v + " ".repeat(f.length() - v.length()));
    } else {
      if (!digits(v)) {
        throw error(f, "value '" + v + "' is not numeric");
      }
      put(f, "0".repeat(f.length() - v.length()) + v);
    }
  }

  public void setNumber(String field, long value) {
    FieldDef f = typed(field, FieldType.NUMERIC);
    String v = Long.toString(value);
    if (value < 0 || v.length() > f.length()) {
      throw error(f, "number " + value + " does not fit in " + f.length() + " digits");
    }
    put(f, "0".repeat(f.length() - v.length()) + v);
  }

  public long getNumber(String field) {
    FieldDef f = typed(field, FieldType.NUMERIC);
    String raw = raw(f);
    if (!digits(raw)) {
      throw error(f, "value '" + raw + "' is not numeric");
    }
    return Long.parseLong(raw);
  }

  /** CCYYMMDD, 4-digit year (D-004). */
  public void setDate(String field, LocalDate date) {
    FieldDef f = typed(field, FieldType.DATE_CCYYMMDD);
    if (date.getYear() < 0 || date.getYear() > 9999) {
      throw error(f, "year " + date.getYear() + " does not fit in CCYY");
    }
    put(
        f,
        String.format("%04d%02d%02d", date.getYear(), date.getMonthValue(), date.getDayOfMonth()));
  }

  public LocalDate getDate(String field) {
    return ccyymmdd(typed(field, FieldType.DATE_CCYYMMDD));
  }

  /** YYDDD: the two-digit year and the day of the year (D-015, legacy T-MSIS only). */
  public void setYyddd(String field, LocalDate date) {
    FieldDef f = typed(field, FieldType.DATE_YYDDD);
    put(f, String.format("%02d%03d", Math.floorMod(date.getYear(), 100), date.getDayOfYear()));
  }

  /**
   * A YYDDD value read with the century the caller gives (the CC of CCYY: 20 for 2000-2099); there
   * is no pivot year (D-004).
   */
  public LocalDate getYyddd(String field, int century) {
    FieldDef f = typed(field, FieldType.DATE_YYDDD);
    String raw = raw(f);
    if (!digits(raw) || raw.length() != 5) {
      throw error(f, "value '" + raw + "' is not a YYDDD date");
    }
    int year = century * 100 + Integer.parseInt(raw.substring(0, 2));
    int day = Integer.parseInt(raw.substring(2));
    if (day < 1 || day > Year.of(year).length()) {
      throw error(f, "day " + day + " does not exist in " + year);
    }
    return LocalDate.ofYearDay(year, day);
  }

  /** The checks a read makes: digits in every numeric and date field, real CCYYMMDD dates. */
  void validate() {
    for (FieldDef f : layout.fields()) {
      if (!f.type().digits() || !inView(f)) {
        continue;
      }
      String raw = raw(f);
      if (!digits(raw)) {
        throw error(f, "value '" + raw + "' is not numeric");
      }
      if (f.type() == FieldType.DATE_CCYYMMDD) {
        ccyymmdd(f);
      }
    }
  }

  private LocalDate ccyymmdd(FieldDef f) {
    String raw = raw(f);
    if (!digits(raw) || raw.length() != 8) {
      throw error(f, "value '" + raw + "' is not a CCYYMMDD date");
    }
    try {
      return LocalDate.of(
          Integer.parseInt(raw.substring(0, 4)),
          Integer.parseInt(raw.substring(4, 6)),
          Integer.parseInt(raw.substring(6)));
    } catch (DateTimeException e) {
      throw error(f, "'" + raw + "' is not a possible date");
    }
  }

  private FieldDef typed(String field, FieldType type) {
    FieldDef f = present(field);
    if (f.type() != type) {
      throw error(f, "field is " + f.type() + ", not " + type);
    }
    return f;
  }

  /** The field, if it exists for this record's view. */
  private FieldDef present(String field) {
    FieldDef f = null;
    for (FieldDef candidate : layout.fields()) {
      if (candidate.name().equals(field)) {
        f = candidate;
        break;
      }
    }
    if (f == null) {
      throw new FixedWidthException(
          layout.name(), recordNumber, field, prefix(field) + "no such field");
    }
    if (!inView(f)) {
      throw error(
          f,
          "field exists only when "
              + layout.discriminator()
              + " is one of "
              + f.views()
              + "; this record has '"
              + discriminatorValue()
              + "'");
    }
    return f;
  }

  private boolean inView(FieldDef f) {
    return f.views().isEmpty() || f.views().contains(discriminatorValue());
  }

  private String discriminatorValue() {
    if (layout.discriminator() == null) {
      return null;
    }
    for (FieldDef f : layout.fields()) {
      if (f.name().equals(layout.discriminator())) {
        return raw(f).trim();
      }
    }
    return null;
  }

  private String raw(FieldDef f) {
    return new String(bytes, f.start() - 1, f.length(), StandardCharsets.ISO_8859_1);
  }

  private void put(FieldDef f, String value) {
    byte[] v = value.getBytes(StandardCharsets.ISO_8859_1);
    System.arraycopy(v, 0, bytes, f.start() - 1, f.length());
  }

  private static boolean digits(String s) {
    if (s.isEmpty()) {
      return false;
    }
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return true;
  }

  private String prefix(String field) {
    return layout.name() + " record " + recordNumber + " field " + field + ": ";
  }

  private FixedWidthException error(FieldDef f, String what) {
    return new FixedWidthException(layout.name(), recordNumber, f.name(), prefix(f.name()) + what);
  }
}
