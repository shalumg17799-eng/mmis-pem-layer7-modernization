package gov.medicaid.pem.enrollment.application;

import java.util.List;
import java.util.UUID;

/**
 * Data of the ApplicationRejected event (contracts/asyncapi/events.yaml#ApplicationRejectedData).
 */
public record ApplicationRejectedData(UUID applicationId, String npi, List<Error> errors) {

  public ApplicationRejectedData {
    errors = List.copyOf(errors);
  }

  /** One failed check: the full four-character code and the field (D-016). */
  public record Error(String code, String field) {}
}
