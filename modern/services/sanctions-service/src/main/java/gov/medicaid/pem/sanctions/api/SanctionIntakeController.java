package gov.medicaid.pem.sanctions.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import gov.medicaid.pem.kit.security.CurrentUser;
import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.sanctions.application.SanctionIntake;
import gov.medicaid.pem.sanctions.application.SanctionIntakeLauncher;
import gov.medicaid.pem.sanctions.application.SanctionIntakeRuns;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** The SANCIN intake runs (contract sanctions-service.yaml; MAM-55). */
@RestController
@RequestMapping("/api/v1/batch/sanction-intake-runs")
public class SanctionIntakeController {

  static final String JOB = "PRVSAN01";

  private final SanctionIntake intake;
  private final SanctionIntakeLauncher launcher;
  private final SanctionIntakeRuns runs;

  public SanctionIntakeController(
      SanctionIntake intake, SanctionIntakeLauncher launcher, SanctionIntakeRuns runs) {
    this.intake = intake;
    this.launcher = launcher;
    this.runs = runs;
  }

  /** Contract BatchRun; counts read, applied, notFound, rejected, skipped and terminated. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record BatchRun(
      UUID runId,
      String job,
      LocalDate businessDate,
      String status,
      Map<String, Integer> counts,
      String error,
      Instant startedAt,
      Instant endedAt) {

    static BatchRun of(SanctionIntakeRuns.Run r) {
      Map<String, Integer> counts = new LinkedHashMap<>();
      counts.put("read", r.read());
      counts.put("applied", r.applied());
      counts.put("notFound", r.notFound());
      counts.put("rejected", r.rejected());
      counts.put("skipped", r.skipped());
      counts.put("terminated", r.terminated());
      return new BatchRun(
          r.runId(),
          JOB,
          r.businessDate(),
          r.status(),
          counts,
          r.error(),
          r.startedAt(),
          r.endedAt());
    }
  }

  /**
   * startSanctionIntakeRun: x-roles ADMIN, SYSTEM; multipart file and businessDate are required
   * (D-020), else 400 and no run. recordedBy is the caller's login name.
   */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.ACCEPTED)
  @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
  public BatchRun startSanctionIntakeRun(
      @RequestPart(name = "file", required = false) MultipartFile file,
      @RequestParam(name = "businessDate", required = false) String businessDate) {
    if (file == null) {
      throw ApiException.badRequest("file is required.");
    }
    if (businessDate == null || businessDate.isBlank()) {
      throw ApiException.badRequest("businessDate is required.");
    }
    LocalDate date;
    try {
      date = LocalDate.parse(businessDate.trim());
    } catch (DateTimeParseException e) {
      throw ApiException.badRequest("businessDate must be an ISO date (yyyy-mm-dd).");
    }
    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException e) {
      throw ApiException.badRequest("the file could not be read.");
    }
    UUID runId = intake.open(date);
    try {
      launcher.launch(runId, date, bytes, login());
    } catch (RuntimeException e) {
      intake.failed(runId, date, e);
    }
    return BatchRun.of(runs.find(runId).orElseThrow());
  }

  /** getSanctionIntakeRun: x-roles ADMIN. */
  @GetMapping("/{runId}")
  @PreAuthorize("hasRole('ADMIN')")
  public BatchRun getSanctionIntakeRun(@PathVariable UUID runId) {
    return BatchRun.of(
        runs.find(runId)
            .orElseThrow(() -> ApiException.notFound("No sanction intake run " + runId)));
  }

  private static String login() {
    CurrentUser user = CurrentUser.current();
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return auth instanceof JwtAuthenticationToken jwt
        ? Optional.ofNullable(jwt.getToken().getClaimAsString("preferred_username"))
            .orElse(user.name())
        : user.name();
  }
}
