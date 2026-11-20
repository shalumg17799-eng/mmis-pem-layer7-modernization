package gov.medicaid.pem.enrollment.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import gov.medicaid.pem.enrollment.application.IntakeLauncher;
import gov.medicaid.pem.enrollment.application.IntakeLoad;
import gov.medicaid.pem.enrollment.application.IntakeRuns;
import gov.medicaid.pem.kit.security.CurrentUser;
import gov.medicaid.pem.kit.web.ApiException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

/** The APPINPUT intake runs (contract enrollment-service.yaml; MAM-56, MAM-57). */
@RestController
@RequestMapping("/api/v1/batch/intake-runs")
public class IntakeRunController {

  static final String JOB = "PRVENR01";
  static final int BATCH_ID_MAX = 8;

  private final IntakeLoad load;
  private final IntakeLauncher launcher;
  private final IntakeRuns runs;

  public IntakeRunController(IntakeLoad load, IntakeLauncher launcher, IntakeRuns runs) {
    this.load = load;
    this.launcher = launcher;
    this.runs = runs;
  }

  /** Contract BatchRun; counts read, written, errored and skipped (MAM-57). */
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

    static BatchRun of(IntakeRuns.Run r) {
      Map<String, Integer> counts = new LinkedHashMap<>();
      counts.put("read", r.read());
      counts.put("written", r.written());
      counts.put("errored", r.errored());
      counts.put("skipped", r.skipped());
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
   * startIntakeRun: x-roles ADMIN, SYSTEM; multipart file and businessDate are required (D-020),
   * batchId is optional (DEFBATCH, D-017); else 400 and no run.
   */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.ACCEPTED)
  @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
  public BatchRun startIntakeRun(
      @RequestPart(name = "file", required = false) MultipartFile file,
      @RequestParam(name = "businessDate", required = false) String businessDate,
      @RequestParam(name = "batchId", required = false) String batchId) {
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
    if (batchId != null && batchId.trim().length() > BATCH_ID_MAX) {
      throw ApiException.badRequest("batchId must be at most " + BATCH_ID_MAX + " characters.");
    }
    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException e) {
      throw ApiException.badRequest("the file could not be read.");
    }
    UUID runId = load.open(date, batchId);
    try {
      launcher.launch(runId, date, bytes, login());
    } catch (RuntimeException e) {
      load.failed(runId, date, e.getMessage() == null ? e.toString() : e.getMessage());
    }
    return BatchRun.of(runs.find(runId).orElseThrow());
  }

  /** getIntakeRun: x-roles ADMIN. */
  @GetMapping("/{runId}")
  @PreAuthorize("hasRole('ADMIN')")
  public BatchRun getIntakeRun(@PathVariable UUID runId) {
    return BatchRun.of(
        runs.find(runId).orElseThrow(() -> ApiException.notFound("No intake run " + runId)));
  }

  /** getIntakeErrorFile: x-roles ADMIN; the APPERROR rows of the run (empty when none). */
  @GetMapping("/{runId}/error-file")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<byte[]> getIntakeErrorFile(@PathVariable UUID runId) {
    String file =
        runs.errorFile(runId).orElseThrow(() -> ApiException.notFound("No intake run " + runId));
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_PLAIN)
        .body(file.getBytes(StandardCharsets.US_ASCII));
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
