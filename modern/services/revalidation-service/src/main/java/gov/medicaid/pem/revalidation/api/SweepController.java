package gov.medicaid.pem.revalidation.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.revalidation.application.RunSweep;
import gov.medicaid.pem.revalidation.application.SweepRun;
import gov.medicaid.pem.revalidation.application.SweepRuns;
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
 * The revalidation sweep (contract revalidation-service.yaml; run status per Q-046 / C-8 answer A).
 */
@RestController
@RequestMapping("/api/v1/batch/revalidation-sweeps")
public class SweepController {

  static final String JOB = "revalidation-sweep";

  private final RunSweep sweep;
  private final SweepRuns runs;

  public SweepController(RunSweep sweep, SweepRuns runs) {
    this.sweep = sweep;
    this.runs = runs;
  }

  /** Contract BatchRunRequest. */
  public record BatchRunRequest(LocalDate businessDate) {}

  /** Contract BatchRun. */
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

    static BatchRun of(SweepRun r) {
      Map<String, Integer> counts = new LinkedHashMap<>();
      counts.put("queued", r.queued());
      counts.put("noDueDate", r.noDueDate());
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

  /** startRevalidationSweep: x-roles ADMIN, SYSTEM; the business date is required (D-020). */
  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
  public BatchRun start(@RequestBody BatchRunRequest request) {
    if (request == null || request.businessDate() == null) {
      throw ApiException.badRequest("businessDate is required.");
    }
    return BatchRun.of(sweep.start(request.businessDate()));
  }

  /** The run with its status, counts and error (Q-046 answer A): x-roles ADMIN. */
  @GetMapping("/{runId}")
  @PreAuthorize("hasRole('ADMIN')")
  public BatchRun run(@PathVariable UUID runId) {
    return BatchRun.of(
        runs.find(runId).orElseThrow(() -> ApiException.notFound("No sweep run " + runId)));
  }

  /** getRevalidationQueueFile: x-roles ADMIN; the REVALQ file of a completed run. */
  @GetMapping("/{runId}/queue-file")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<byte[]> queueFile(@PathVariable UUID runId) {
    byte[] file =
        runs.queueFile(runId)
            .orElseThrow(() -> ApiException.notFound("No REVALQ file for sweep run " + runId));
    return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(file);
  }
}
