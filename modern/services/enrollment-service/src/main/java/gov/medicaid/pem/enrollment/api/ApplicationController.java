package gov.medicaid.pem.enrollment.api;

import gov.medicaid.pem.enrollment.application.ApplicationPage;
import gov.medicaid.pem.enrollment.application.ListApplications;
import gov.medicaid.pem.enrollment.application.StoredApplication;
import gov.medicaid.pem.enrollment.application.SubmitApplication;
import gov.medicaid.pem.kit.security.CurrentUser;
import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.kit.web.ApiFieldError;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Applications API (contract enrollment-service.yaml). */
@RestController
@Validated
@RequestMapping("/api/v1/applications")
public class ApplicationController {

  private final SubmitApplication submit;
  private final ListApplications list;
  private final gov.medicaid.pem.enrollment.application.ReadApplication read;

  public ApplicationController(
      SubmitApplication submit,
      ListApplications list,
      gov.medicaid.pem.enrollment.application.ReadApplication read) {
    this.submit = submit;
    this.list = list;
    this.read = read;
  }

  /** getApplication: x-roles PROVIDER (own only), ANALYST, SUPERVISOR (MAM-54). */
  @GetMapping("/{applicationId}")
  @PreAuthorize("hasAnyRole('PROVIDER', 'ANALYST', 'SUPERVISOR')")
  public ApplicationResponse getApplication(
      @org.springframework.web.bind.annotation.PathVariable java.util.UUID applicationId) {
    return ApplicationResponse.of(read.byId(applicationId, CurrentUser.current()));
  }

  /**
   * submitApplication: x-roles PROVIDER, ANALYST (D-023). 201 when VALIDATED, 422 with every error
   * when REJECTED.
   */
  @PostMapping
  @PreAuthorize("hasAnyRole('PROVIDER', 'ANALYST')")
  public ResponseEntity<ApplicationResponse> submitApplication(
      @Valid @RequestBody ApplicationRequest request) {
    StoredApplication a = submit.submit(request.toCommand(), CurrentUser.current());
    if (!a.errors().isEmpty()) {
      List<ApiFieldError> errors =
          a.errors().stream()
              .map(e -> new ApiFieldError(e.field(), e.code().name(), e.code().message()))
              .toList();
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          errors.size() == 1 ? errors.get(0).code() : null,
          "The application failed validation.",
          errors);
    }
    return ResponseEntity.status(HttpStatus.CREATED).body(ApplicationResponse.of(a));
  }

  /** listApplications: x-roles PROVIDER (own only), ANALYST, SUPERVISOR. */
  @GetMapping
  @PreAuthorize("hasAnyRole('PROVIDER', 'ANALYST', 'SUPERVISOR')")
  public Map<String, Object> listApplications(
      @RequestParam(required = false) @Pattern(regexp = "SUBMITTED|VALIDATED|REJECTED")
          String status,
      @RequestParam(required = false) @Pattern(regexp = "^[0-9]{10}$") String npi,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
    ApplicationPage p = list.list(status, npi, page, size, CurrentUser.current());
    return Map.of(
        "items", p.items().stream().map(ApplicationResponse::of).toList(),
        "page", p.page(),
        "size", p.size(),
        "total", p.total());
  }
}
