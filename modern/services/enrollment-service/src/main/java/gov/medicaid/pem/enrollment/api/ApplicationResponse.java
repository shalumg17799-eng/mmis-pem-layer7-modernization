package gov.medicaid.pem.enrollment.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import gov.medicaid.pem.enrollment.application.NewApplication;
import gov.medicaid.pem.enrollment.application.StoredApplication;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contract Application. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApplicationResponse(
    UUID applicationId,
    String status,
    @JsonInclude(JsonInclude.Include.ALWAYS) List<FieldError> errors,
    String npi,
    String medicaidId,
    String providerType,
    String taxonomy,
    NewApplication.Individual individual,
    NewApplication.Organization organization,
    String source,
    Instant submittedAt) {

  /** Contract FieldError. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record FieldError(String code, String field, String message) {}

  static ApplicationResponse of(StoredApplication a) {
    return new ApplicationResponse(
        a.applicationId(),
        a.status(),
        a.errors().stream()
            .map(e -> new FieldError(e.code().name(), e.field(), e.code().message()))
            .toList(),
        a.npi() == null ? "" : a.npi(),
        a.medicaidId(),
        a.providerType(),
        a.taxonomy(),
        a.individual(),
        a.organization(),
        a.source(),
        a.submittedAt());
  }
}
