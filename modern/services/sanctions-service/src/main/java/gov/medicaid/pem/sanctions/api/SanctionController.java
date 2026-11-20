package gov.medicaid.pem.sanctions.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import gov.medicaid.pem.kit.security.CurrentUser;
import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.sanctions.application.ListSanctions;
import gov.medicaid.pem.sanctions.application.RecordSanction;
import gov.medicaid.pem.sanctions.application.SanctionCommand;
import gov.medicaid.pem.sanctions.application.SanctionPage;
import gov.medicaid.pem.sanctions.domain.Sanction;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Sanction API (contract sanctions-service.yaml, MAM-52). */
@RestController
@RequestMapping("/api/v1/sanctions")
public class SanctionController {

  private static final Pattern NPI = Pattern.compile("[0-9]{10}");

  private final RecordSanction record;
  private final ListSanctions list;

  public SanctionController(RecordSanction record, ListSanctions list) {
    this.record = record;
    this.list = list;
  }

  /** Contract SanctionRequest; the action is taken as text so an unknown one is a 422 (D-026). */
  public record SanctionRequest(
      String npi, String action, String reason, LocalDate effectiveDate, String sourceFeed) {}

  /** Contract Sanction. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SanctionResponse(
      UUID sanctionId,
      String npi,
      String action,
      String reason,
      LocalDate effectiveDate,
      String sourceFeed,
      String recordedBy,
      Instant recordedAt,
      String resultingStatus) {

    static SanctionResponse of(Sanction s) {
      return new SanctionResponse(
          s.sanctionId(),
          s.npi(),
          s.action().name(),
          s.reason(),
          s.effectiveDate(),
          s.sourceFeed(),
          s.recordedBy(),
          s.recordedAt(),
          s.resultingStatus());
    }
  }

  /** Contract SanctionPage. */
  public record SanctionPageResponse(
      List<SanctionResponse> items, int page, int size, long total) {}

  /** recordSanction: x-roles SUPERVISOR (D-023); recordedBy is the supervisor's login name. */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('SUPERVISOR')")
  public SanctionResponse recordSanction(@RequestBody SanctionRequest request) {
    return SanctionResponse.of(
        record.record(
            new SanctionCommand(
                request.npi(),
                request.action(),
                request.reason(),
                request.effectiveDate(),
                request.sourceFeed()),
            login()));
  }

  /** listSanctions: x-roles ANALYST, SUPERVISOR, ADMIN (D-023); newest first. */
  @GetMapping
  @PreAuthorize("hasAnyRole('ANALYST', 'SUPERVISOR', 'ADMIN')")
  public SanctionPageResponse listSanctions(
      @RequestParam(required = false) String npi,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    if (npi != null && !NPI.matcher(npi).matches()) {
      throw ApiException.badRequest("The NPI must be 10 digits.");
    }
    if (page < 0 || size < 1 || size > 200) {
      throw ApiException.badRequest("page must be 0 or more and size 1 to 200.");
    }
    SanctionPage p = list.page(npi, page, size);
    return new SanctionPageResponse(
        p.items().stream().map(SanctionResponse::of).toList(), page, size, p.total());
  }

  /** The preferred_username claim of the token when there is one, else the principal name. */
  private static String login() {
    CurrentUser user = CurrentUser.current();
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return auth instanceof JwtAuthenticationToken jwt
        ? Optional.ofNullable(jwt.getToken().getClaimAsString("preferred_username"))
            .orElse(user.name())
        : user.name();
  }
}
