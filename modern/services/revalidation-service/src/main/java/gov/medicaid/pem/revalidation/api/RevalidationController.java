package gov.medicaid.pem.revalidation.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.revalidation.application.CasePage;
import gov.medicaid.pem.revalidation.application.CompleteCase;
import gov.medicaid.pem.revalidation.application.ListCases;
import gov.medicaid.pem.revalidation.domain.CaseStatus;
import gov.medicaid.pem.revalidation.domain.RevalidationCase;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Revalidation cases (contract revalidation-service.yaml#listRevalidationCases). */
@RestController
@RequestMapping("/api/v1/revalidations")
public class RevalidationController {

  private static final Pattern NPI = Pattern.compile("[0-9]{10}");

  private final ListCases list;
  private final CompleteCase complete;

  public RevalidationController(ListCases list, CompleteCase complete) {
    this.list = list;
    this.complete = complete;
  }

  /** Contract CompletionRequest. */
  public record CompletionRequest(UUID screeningId, String result) {}

  /**
   * completeRevalidation (MAM-48): x-roles SYSTEM, ANALYST; 404 unknown case, 409 closed case. The
   * result is PASS or FAIL; either closes the case (C-12); the next due date stays with
   * provider-service (D-003).
   */
  @PostMapping("/{caseId}/completion")
  @PreAuthorize("hasAnyRole('SYSTEM', 'ANALYST')")
  public CaseResponse completeRevalidation(
      @PathVariable UUID caseId, @RequestBody CompletionRequest request) {
    if (request == null
        || request.screeningId() == null
        || !("PASS".equals(request.result()) || "FAIL".equals(request.result()))) {
      throw ApiException.badRequest("screeningId and a result of PASS or FAIL are required.");
    }
    return CaseResponse.of(complete.byRequest(caseId, request.screeningId(), request.result()));
  }

  /** Contract RevalidationCase. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record CaseResponse(
      UUID caseId,
      String npi,
      LocalDate dueDate,
      String status,
      Instant openedAt,
      Instant closedAt) {

    static CaseResponse of(RevalidationCase c) {
      return new CaseResponse(
          c.caseId(), c.npi(), c.dueDate(), c.status().name(), c.openedAt(), c.closedAt());
    }
  }

  /** Contract RevalidationCasePage. */
  public record CasePageResponse(List<CaseResponse> items, int page, int size, long total) {}

  /** listRevalidationCases: x-roles ANALYST, SUPERVISOR. */
  @GetMapping
  @PreAuthorize("hasAnyRole('ANALYST', 'SUPERVISOR')")
  public CasePageResponse list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String npi,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    if (npi != null && !NPI.matcher(npi).matches()) {
      throw ApiException.badRequest("The NPI must be 10 digits.");
    }
    if (page < 0 || size < 1 || size > 200) {
      throw ApiException.badRequest("page must be 0 or more and size 1 to 200.");
    }
    CaseStatus s = null;
    if (status != null) {
      try {
        s = CaseStatus.valueOf(status);
      } catch (IllegalArgumentException e) {
        throw ApiException.badRequest(
            "status must be one of DUE, IN_SCREENING, COMPLETED or CANCELLED.");
      }
    }
    CasePage p = list.page(npi, s, page, size);
    return new CasePageResponse(
        p.items().stream().map(CaseResponse::of).toList(), page, size, p.total());
  }
}
