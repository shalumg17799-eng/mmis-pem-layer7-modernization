package gov.medicaid.pem.fixedwidth;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads and writes the legacy fixed-width records byte for byte (MAM-42). The layouts are the JSON
 * files exported from the knowledge graph into {@code /layouts/<NAME>.json}
 * (modern/tools/data/export_layouts.py); they are never typed by hand.
 */
public final class FixedWidthCodec {

  private static final ObjectMapper JSON =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private static final Map<String, RecordLayout> LOADED = new ConcurrentHashMap<>();

  private final RecordLayout layout;

  private FixedWidthCodec(RecordLayout layout) {
    this.layout = layout;
  }

  /** The codec of an exported layout, e.g. PRVRECRD, APPINPUT, SANCIN, REVALQ, TMSISOUT. */
  public static FixedWidthCodec forLayout(String name) {
    return new FixedWidthCodec(LOADED.computeIfAbsent(name, FixedWidthCodec::load));
  }

  public static FixedWidthCodec forLayout(RecordLayout layout) {
    return new FixedWidthCodec(layout);
  }

  public RecordLayout layout() {
    return layout;
  }

  /** A blank record: alphanumeric fields spaces, numeric and date fields zeros. */
  public FixedRecord newRecord() {
    byte[] bytes = new byte[layout.length()];
    Arrays.fill(bytes, (byte) ' ');
    for (FieldDef f : layout.fields()) {
      if (f.type().digits()) {
        Arrays.fill(bytes, f.start() - 1, f.end(), (byte) '0');
      }
    }
    return new FixedRecord(layout, bytes, 0);
  }

  /**
   * Reads one record; {@code recordNumber} is only used to name the record in an error. A wrong
   * length, a non-numeric digit field or an impossible date is an error (D-024).
   */
  public FixedRecord read(byte[] record, long recordNumber) {
    int actual = record == null ? 0 : record.length;
    if (actual != layout.length()) {
      throw new FixedWidthException(
          layout.name(),
          recordNumber,
          null,
          layout.name()
              + " record "
              + recordNumber
              + ": "
              + actual
              + " bytes, but the layout is "
              + layout.length()
              + " bytes");
    }
    FixedRecord r = new FixedRecord(layout, Arrays.copyOf(record, actual), recordNumber);
    r.validate();
    return r;
  }

  /** The record's bytes: exactly {@code layout().length()} of them. */
  public byte[] write(FixedRecord record) {
    if (!record.layout().equals(layout)) {
      throw new FixedWidthException(
          layout.name(),
          0,
          null,
          "a " + record.layout().name() + " record cannot be written as " + layout.name());
    }
    return record.bytes();
  }

  private static RecordLayout load(String name) {
    try (InputStream in = FixedWidthCodec.class.getResourceAsStream("/layouts/" + name + ".json")) {
      if (in == null) {
        throw new FixedWidthException(name, 0, null, "no layout file layouts/" + name + ".json");
      }
      return JSON.readValue(in, RecordLayout.class);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read layout " + name, e);
    }
  }
}
