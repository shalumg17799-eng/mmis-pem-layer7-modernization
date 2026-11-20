package gov.medicaid.pem.screening.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.screening.application.ReadScreenings;
import gov.medicaid.pem.screening.application.RequestScreening;
import gov.medicaid.pem.screening.application.Screening;
import gov.medicaid.pem.screening.application.ScreeningPage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Screenings read API (contract screening-service.yaml): staff only (D-023). */
@RestController
@RequestMapping("/api/v1/screenings")
public class ScreeningController {

  private final ReadScreenings read;
  private final RequestScreening request;

  public ScreeningController(ReadScreenings read, RequestScreening request) {
    this.read = read;
    this.request = request;
  }

  /** Contract ScreeningRequest. */
  public record ScreeningRequest(String npi) {}

  /**
   * requestScreening (MAM-28): x-roles ANALYST, SUPERVISOR, SYSTEM; 202 with the RUNNING MANUAL
   * screening, 400 for a malformed NPI, 404 for an NPI provider-service does not know.
   */
  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  @PreAuthorize("hasAnyRole('ANALYST', 'SUPERVISOR', 'SYSTEM')")
  public ScreeningResponse requestScreening(@RequestBody ScreeningRequest body) {
    if (body == null || body.npi() == null || !body.npi().matches("[0-9]{10}")) {
      throw ApiException.badRequest("The NPI must be 10 digits.");
    }
    return ScreeningResponse.of(request.request(body.npi()));
  }

  /** listScreenings: x-roles ANALYST, SUPERVISOR, ADMIN. */
  @GetMapping
  @PreAuthorize("hasAnyRole('ANALYST', 'SUPERVISOR', 'ADMIN')")
  public Map<String, Object> listScreenings(
      @RequestParam(required = false) String npi,
      @RequestParam(required = false) String result,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    if (npi != null && !npi.matches("[0-9]{10}")) {
      throw ApiException.badRequest("The NPI must be 10 digits.");
    }
    if (result != null && !result.matches("PASS|FAIL")) {
      throw ApiException.badRequest("result must be PASS or FAIL.");
    }
    if (page < 0 || size < 1 || size > 200) {
      throw ApiException.badRequest("page must be 0 or more and size 1 to 200.");
    }
    ScreeningPage p = read.list(npi, result, page, size);
    return Map.of(
        "items", p.items().stream().map(ScreeningResponse::of).toList(),
        "page", p.page(),
        "size", p.size(),
        "total", p.total());
  }

  /** getScreening: x-roles ANALYST, SUPERVISOR, ADMIN. */
  @GetMapping("/{screeningId}")
  @PreAuthorize("hasAnyRole('ANALYST', 'SUPERVISOR', 'ADMIN')")
  public ScreeningResponse getScreening(@PathVariable UUID screeningId) {
    return ScreeningResponse.of(read.get(screeningId));
  }

  /** Contract Screening. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record ScreeningResponse(
      UUID screeningId,
      String npi,
      String reason,
      String status,
      String result,
      List<Result> results,
      Instant startedAt,
      Instant completedAt) {

    /** Contract SourceResult. */
    public record Result(
        String source, String result, Instant verdictAt, String detail, String errorCode) {}

    static ScreeningResponse of(Screening s) {
      return new ScreeningResponse(
          s.screeningId(),
          s.npi(),
          s.reason(),
          s.status(),
          s.result(),
          s.results().stream()
              .map(
                  r ->
                      new Result(
                          r.source().name(), r.result(), r.verdictAt(), r.detail(), r.errorCode()))
              .toList(),
          s.startedAt(),
          s.completedAt());
    }
  }
}
