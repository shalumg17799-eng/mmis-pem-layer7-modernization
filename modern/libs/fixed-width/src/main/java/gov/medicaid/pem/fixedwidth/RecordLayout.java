package gov.medicaid.pem.fixedwidth;

import java.util.List;

/**
 * A fixed-width record layout, exported from the knowledge graph (MAM-42 AC-1). {@code
 * discriminator} names the field whose value picks a REDEFINES view, or is null.
 */
public record RecordLayout(
    String name, int length, String readQuery, String discriminator, List<FieldDef> fields) {

  public RecordLayout {
    fields = List.copyOf(fields);
    for (FieldDef f : fields) {
      if (f.start() < 1 || f.length() < 1 || f.end() > length) {
        throw new FixedWidthException(
            name,
            0,
            f.name(),
            "layout " + name + ": field " + f.name() + " lies outside the " + length + " bytes");
      }
    }
  }

  /** The field with this name; an unknown name is an error. */
  public FieldDef field(String fieldName) {
    for (FieldDef f : fields) {
      if (f.name().equals(fieldName)) {
        return f;
      }
    }
    throw new FixedWidthException(
        name, 0, fieldName, "layout " + name + " has no field " + fieldName);
  }
}
