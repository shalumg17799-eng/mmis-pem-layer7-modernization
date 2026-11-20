package gov.medicaid.pem.tmsis.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.tmsis.application.ExtractRun;
import gov.medicaid.pem.tmsis.application.ExtractRuns;
import gov.medicaid.pem.tmsis.application.StartTmsisExtract;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.LinkedHashMap;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The T-MSIS extract runs (contract tmsis-service.yaml; MAM-71, MAM-75). */
@RestController
@RequestMapping("/api/v1/batch/tmsis-extracts")
public class TmsisExtractController {

  /** The legacy program id (MAM-75 AC-1). */
  static final String JOB = "PRVTMS01";

  /** The contract's format date: yyyy-MM-dd and nothing else, an impossible date is no date. */
  private static final DateTimeFormatter ISO_DATE =
      DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

  private final StartTmsisExtract start;
  private final ExtractRuns runs;

  public TmsisExtractController(StartTmsisExtract start, ExtractRuns runs) {
    this.start = start;
    this.runs = runs;
  }

  /**
   * Contract BatchRunRequest. The date is read as text and parsed strictly: a date-time such as
   * 2026-03-31T00:00:00Z, a number or an array is not a date, and Jackson's default LocalDate
   * reading would accept them.
   */
  public record BatchRunRequest(String businessDate) {}

  /**
   * Contract BatchRun; counts.rows, counts.futureEnrolments (MAM-78) and counts.archiveGeneration
   * (Q-060 default A).
   */
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

    static BatchRun of(ExtractRun r) {
      Map<String, Integer> counts = new LinkedHashMap<>();
      counts.put("rows", r.rows());
      counts.put("futureEnrolments", r.futureEnrolments());
      if (r.archiveGeneration() != null) {
        counts.put("archiveGeneration", r.archiveGeneration());
      }
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

  /** startTmsisExtract: x-roles ADMIN, SYSTEM; the business date is required (D-020). */
  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
  public BatchRun startTmsisExtract(@RequestBody(required = false) BatchRunRequest request) {
    if (request == null || request.businessDate() == null) {
      throw ApiException.badRequest("businessDate is required.");
    }
    return BatchRun.of(start.start(parseBusinessDate(request.businessDate())));
  }

  private static LocalDate parseBusinessDate(String text) {
    try {
      return LocalDate.parse(text, ISO_DATE);
    } catch (DateTimeParseException e) {
      throw ApiException.badRequest("businessDate must be a date in the form yyyy-mm-dd.");
    }
  }

  /** listTmsisExtracts: x-roles ADMIN; newest run first. */
  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public Map<String, Object> listTmsisExtracts(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    if (page < 0 || size < 1 || size > 200) {
      throw ApiException.badRequest("page must be 0 or more and size 1 to 200.");
    }
    ExtractRuns.RunPage p = runs.page(page, size);
    List<BatchRun> items = p.items().stream().map(BatchRun::of).toList();
    return Map.of("items", items, "page", page, "size", size, "total", p.total());
  }

  /** getTmsisExtractFile: x-roles ADMIN; the archived TMSISOUT file of a COMPLETED run. */
  @GetMapping("/{runId}/file")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<byte[]> getTmsisExtractFile(@PathVariable UUID runId) {
    byte[] file =
        runs.file(runId)
            .orElseThrow(() -> ApiException.notFound("No T-MSIS file for extract run " + runId));
    return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(file);
  }
}
