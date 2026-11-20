package gov.medicaid.pem.provider.api;

import gov.medicaid.pem.kit.security.CurrentUser;
import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.provider.application.AuditEntry;
import gov.medicaid.pem.provider.application.AuditHistory;
import gov.medicaid.pem.provider.application.ChangeProviderStatus;
import gov.medicaid.pem.provider.application.FindProvider;
import gov.medicaid.pem.provider.application.ProviderSearch;
import gov.medicaid.pem.provider.application.ReadProviderHistory;
import gov.medicaid.pem.provider.application.StatusChangeCommand;
import gov.medicaid.pem.provider.domain.EnrollStatus;
import gov.medicaid.pem.provider.domain.ScreenFlag;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Provider read API (contract provider-service.yaml). */
@RestController
@RequestMapping("/api/v1/providers")
public class ProviderController {

  private static final Pattern NPI = Pattern.compile("[0-9]{10}");

  private final FindProvider find;
  private final gov.medicaid.pem.provider.application.ProviderSearch search;
  private final gov.medicaid.pem.provider.application.DecideProvider decide;
  private final ChangeProviderStatus changeStatus;
  private final ReadProviderHistory history;

  public ProviderController(
      FindProvider find,
      gov.medicaid.pem.provider.application.ProviderSearch search,
      gov.medicaid.pem.provider.application.DecideProvider decide,
      ChangeProviderStatus changeStatus,
      ReadProviderHistory history) {
    this.find = find;
    this.search = search;
    this.decide = decide;
    this.changeStatus = changeStatus;
    this.history = history;
  }

  /**
   * Contract StatusChangeRequest. The action is taken as text so that an unknown one is refused
   * with 422 by the use case (D-026), not as an unreadable body.
   */
  public record StatusChangeRequest(
      String action, String reason, LocalDate effectiveDate, UUID sanctionId) {}

  /**
   * changeProviderStatus (MAM-33): x-roles SYSTEM, SUPERVISOR (D-013, D-023). The actor is the
   * caller's login name (preferred_username), e.g. service-account-pem-services.
   */
  @PostMapping("/{npi}/status-changes")
  @PreAuthorize("hasAnyRole('SYSTEM', 'SUPERVISOR')")
  public ProviderResponse changeProviderStatus(
      @PathVariable String npi, @RequestBody StatusChangeRequest request) {
    if (!NPI.matcher(npi).matches()) {
      throw ApiException.badRequest("The NPI must be 10 digits.");
    }
    return ProviderResponse.of(
        changeStatus.change(
            npi,
            new StatusChangeCommand(
                request.action(), request.reason(), request.effectiveDate(), request.sanctionId()),
            signedIn()));
  }

  /** The signed-in user, named by the preferred_username claim of the token when there is one. */
  private static CurrentUser signedIn() {
    CurrentUser user = CurrentUser.current();
    var auth = SecurityContextHolder.getContext().getAuthentication();
    String login =
        auth instanceof JwtAuthenticationToken jwt
            ? Optional.ofNullable(jwt.getToken().getClaimAsString("preferred_username"))
                .orElse(user.name())
            : user.name();
    return new CurrentUser(login, user.roles(), user.npi());
  }

  /** Contract ApprovalRequest. */
  public record ApprovalRequest(
      @jakarta.validation.constraints.NotNull gov.medicaid.pem.provider.domain.Decision decision,
      @jakarta.validation.constraints.Size(max = 200) String reason) {}

  /**
   * decideProvider: x-roles ANALYST, SUPERVISOR (D-023). The actor is the user's login name (the
   * preferred_username claim of the Keycloak token).
   */
  @org.springframework.web.bind.annotation.PostMapping("/{npi}/approval")
  @PreAuthorize("hasAnyRole('ANALYST', 'SUPERVISOR')")
  public ProviderResponse decideProvider(
      @PathVariable String npi,
      @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody
          ApprovalRequest request) {
    if (!NPI.matcher(npi).matches()) {
      throw ApiException.badRequest("The NPI must be 10 digits.");
    }
    CurrentUser user = CurrentUser.current();
    var auth =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication();
    String login =
        auth
                instanceof
                org.springframework.security.oauth2.server.resource.authentication
                            .JwtAuthenticationToken
                        jwt
            ? java.util.Optional.ofNullable(jwt.getToken().getClaimAsString("preferred_username"))
                .orElse(user.name())
            : user.name();
    return ProviderResponse.of(
        decide.decide(
            npi,
            request.decision(),
            request.reason(),
            new CurrentUser(login, user.roles(), user.npi())));
  }

  /** getProvider: x-roles PROVIDER (own NPI only), ANALYST, SUPERVISOR, ADMIN, SYSTEM. */
  @GetMapping("/{npi}")
  @PreAuthorize("hasAnyRole('PROVIDER', 'ANALYST', 'SUPERVISOR', 'ADMIN', 'SYSTEM')")
  public ProviderResponse getProvider(@PathVariable String npi) {
    if (!NPI.matcher(npi).matches()) {
      throw ApiException.badRequest("The NPI must be 10 digits.");
    }
    return ProviderResponse.of(find.byNpi(npi, CurrentUser.current()));
  }

  /**
   * searchProviders (MAM-37): x-roles ANALYST, SUPERVISOR, ADMIN, SYSTEM. Every given filter must
   * match: exact NPI, exact Medicaid ID, name contains, status and screen flag; with no filter the
   * page lists every provider (work queues). An NPI that is not on file gives an empty page.
   */
  @GetMapping
  @PreAuthorize("hasAnyRole('ANALYST', 'SUPERVISOR', 'ADMIN', 'SYSTEM')")
  public Map<String, Object> searchProviders(
      @RequestParam(required = false) String npi,
      @RequestParam(required = false) String medicaidId,
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String screenFlag,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    if (npi != null && !NPI.matcher(npi).matches()) {
      throw ApiException.badRequest("The NPI must be 10 digits.");
    }
    if (medicaidId != null && (medicaidId.isBlank() || medicaidId.length() > 9)) {
      throw ApiException.badRequest("The Medicaid ID must be 1 to 9 characters.");
    }
    pageable(page, size);
    ProviderSearch.Page p;
    try {
      p =
          search.find(
              new ProviderSearch.Criteria(
                  npi,
                  medicaidId,
                  name,
                  status == null ? null : EnrollStatus.fromCode(status),
                  screenFlag == null ? null : ScreenFlag.fromCode(screenFlag)),
              page,
              size);
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest(e.getMessage());
    }
    List<ProviderResponse> items = p.items().stream().map(ProviderResponse::of).toList();
    return Map.of("items", items, "page", page, "size", size, "total", p.total());
  }

  /**
   * getProviderAudit (MAM-40): x-roles SUPERVISOR, ADMIN. The provider's history, oldest first and
   * paged; 404 when the NPI is not on file. Only GET is defined: the history is append-only.
   */
  @GetMapping("/{npi}/audit")
  @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
  public Map<String, Object> getProviderAudit(
      @PathVariable String npi,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    if (!NPI.matcher(npi).matches()) {
      throw ApiException.badRequest("The NPI must be 10 digits.");
    }
    pageable(page, size);
    AuditHistory.Page p = history.of(npi, page, size);
    List<Map<String, Object>> items = p.items().stream().map(ProviderController::entry).toList();
    return Map.of("items", items, "page", page, "size", size, "total", p.total());
  }

  private static Map<String, Object> entry(AuditEntry e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("at", e.at());
    m.put("actor", e.actor());
    m.put("action", e.action());
    m.put("before", e.before());
    m.put("after", e.after());
    if (e.correlationId() != null) {
      m.put("correlationId", e.correlationId());
    }
    return m;
  }

  private static void pageable(int page, int size) {
    if (page < 0 || size < 1 || size > 200) {
      throw ApiException.badRequest("page must be 0 or more and size 1 to 200.");
    }
  }
}
