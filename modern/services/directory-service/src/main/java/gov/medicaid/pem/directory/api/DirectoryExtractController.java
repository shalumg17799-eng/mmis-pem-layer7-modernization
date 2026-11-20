package gov.medicaid.pem.directory.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.OptBoolean;
import gov.medicaid.pem.directory.application.DirectoryExtracts;
import gov.medicaid.pem.directory.application.StartDirectoryExtract;
import gov.medicaid.pem.kit.web.ApiException;
import java.time.Instant;
import java.time.LocalDate;
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

/** The directory extract runs (contract directory-service.yaml; MAM-67). */
@RestController
@RequestMapping("/api/v1/batch/directory-extracts")
public class DirectoryExtractController {

  /** The legacy program id (AC-1). */
  static final String JOB = "PRVDIR01";

  private final StartDirectoryExtract start;
  private final DirectoryExtracts runs;

  public DirectoryExtractController(StartDirectoryExtract start, DirectoryExtracts runs) {
    this.start = start;
    this.runs = runs;
  }

  /**
   * Contract BatchRunRequest. The date is strictly yyyy-MM-dd (contract format date, ADR-009): a
   * date-time such as 2026-03-31T00:00:00Z is not a date, and Jackson's default LocalDate reading
   * would accept it.
   */
  public record BatchRunRequest(
      @JsonFormat(pattern = "uuuu-MM-dd", lenient = OptBoolean.FALSE) LocalDate businessDate) {}

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

    static BatchRun of(DirectoryExtracts.Run r) {
      return new BatchRun(
          r.runId(),
          JOB,
          r.businessDate(),
          r.status(),
          Map.of("rows", r.rows()),
          r.error(),
          r.startedAt(),
          r.endedAt());
    }
  }

  /** startDirectoryExtract: x-roles ADMIN, SYSTEM; the business date is required (D-020). */
  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
  public BatchRun startDirectoryExtract(@RequestBody(required = false) BatchRunRequest request) {
    if (request == null || request.businessDate() == null) {
      throw ApiException.badRequest("businessDate is required.");
    }
    return BatchRun.of(start.start(request.businessDate()));
  }

  /** The run with its status, counts and error (contract 1.1.0, user decision): x-roles ADMIN. */
  @GetMapping("/{runId}")
  @PreAuthorize("hasRole('ADMIN')")
  public BatchRun getDirectoryExtract(@PathVariable UUID runId) {
    return BatchRun.of(
        runs.find(runId)
            .orElseThrow(() -> ApiException.notFound("No directory extract run " + runId)));
  }

  /** getDirectoryExtractFile: x-roles ADMIN; the DIROUT file of a COMPLETED run. */
  @GetMapping("/{runId}/file")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<byte[]> getDirectoryExtractFile(@PathVariable UUID runId) {
    byte[] file =
        runs.file(runId)
            .orElseThrow(
                () -> ApiException.notFound("No DIROUT file for directory extract run " + runId));
    return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(file);
  }
}
