package gov.medicaid.pem.fixedwidth;

import java.util.Set;

/**
 * One field of a layout. {@code start} is 1-based; {@code views} holds the discriminator values for
 * which the field exists (a REDEFINES view), empty when the field is always present.
 */
public record FieldDef(String name, int start, int length, FieldType type, Set<String> views) {

  public FieldDef {
    views = views == null ? Set.of() : Set.copyOf(views);
  }

  int end() {
    return start + length - 1;
  }
}
