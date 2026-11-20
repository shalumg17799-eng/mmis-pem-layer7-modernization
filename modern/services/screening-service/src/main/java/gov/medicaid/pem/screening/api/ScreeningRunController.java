package gov.medicaid.pem.screening.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.screening.application.ScreeningRuns;
import gov.medicaid.pem.screening.application.StartScreeningBatch;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The batch screening runs (contract screening-service.yaml; MAM-32). The run-status GET follows
 * question C-8 answer A, approved by the user for contract 1.1.0.
 */
@RestController
@RequestMapping("/api/v1/batch/screening-runs")
public class ScreeningRunController {

  static final String JOB = "PRVSCR01";

  private final StartScreeningBatch start;
  private final ScreeningRuns runs;

  public ScreeningRunController(StartScreeningBatch start, ScreeningRuns runs) {
    this.start = start;
    this.runs = runs;
  }

  /** Contract BatchRunRequest. */
  public record BatchRunRequest(LocalDate businessDate) {}

  /** Contract BatchRun; counts read, screened, skipped and failed (AC-5). */
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

    static BatchRun of(ScreeningRuns.Run r) {
      Map<String, Integer> counts = new LinkedHashMap<>();
      counts.put("read", r.read());
      counts.put("screened", r.screened());
      counts.put("skipped", r.skipped());
      counts.put("failed", r.failed());
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

  /** startScreeningRun: x-roles ADMIN, SYSTEM; the business date is required (D-020). */
  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
  public BatchRun startScreeningRun(@RequestBody(required = false) BatchRunRequest request) {
    if (request == null || request.businessDate() == null) {
      throw ApiException.badRequest("businessDate is required.");
    }
    return BatchRun.of(start.start(request.businessDate()));
  }

  /** The run with its status, counts and error (question C-8 answer A): x-roles ADMIN. */
  @GetMapping("/{runId}")
  @PreAuthorize("hasRole('ADMIN')")
  public BatchRun getScreeningRun(@PathVariable UUID runId) {
    return BatchRun.of(
        runs.find(runId).orElseThrow(() -> ApiException.notFound("No screening run " + runId)));
  }

  /** getScreeningResultsFile: x-roles ADMIN; the SCRNOUT file of a COMPLETED run. */
  @GetMapping("/{runId}/results-file")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<byte[]> getScreeningResultsFile(@PathVariable UUID runId) {
    byte[] file =
        runs.file(runId)
            .orElseThrow(() -> ApiException.notFound("No SCRNOUT file for screening run " + runId));
    return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(file);
  }
}
