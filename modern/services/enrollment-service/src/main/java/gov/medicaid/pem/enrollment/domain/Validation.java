package gov.medicaid.pem.enrollment.domain;

import java.util.List;

/**
 * The outcome of the checks: every failed check once, and the legacy provider type (null when the
 * type has none).
 */
public record Validation(List<ValidationError> errors, String providerType) {

  public Validation {
    errors = List.copyOf(errors);
  }

  public boolean valid() {
    return errors.isEmpty();
  }
}
