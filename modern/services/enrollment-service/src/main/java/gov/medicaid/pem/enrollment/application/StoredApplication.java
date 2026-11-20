package gov.medicaid.pem.enrollment.application;

import gov.medicaid.pem.enrollment.domain.ValidationError;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An application as kept: status VALIDATED or REJECTED with one error per failed check (D-001). */
public record StoredApplication(
    UUID applicationId,
    String status,
    List<ValidationError> errors,
    String npi,
    String medicaidId,
    String providerType,
    String taxonomy,
    NewApplication.Individual individual,
    NewApplication.Organization organization,
    String source,
    Instant submittedAt) {

  public StoredApplication {
    errors = List.copyOf(errors);
  }
}
