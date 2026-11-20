package gov.medicaid.pem.screening.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/**
 * Data of the ScreeningCompleted event (contracts/asyncapi/events.yaml#ScreeningCompletedData). A
 * provider without a taxonomy gives an event without a taxonomy member: the contract says string,
 * never null (MAM-25 AC-5).
 */
public record ScreeningCompletedData(
    UUID screeningId,
    String npi,
    String reason,
    String result,
    @JsonInclude(JsonInclude.Include.NON_NULL) String taxonomy,
    List<SourceSummary> sources) {

  public ScreeningCompletedData {
    sources = List.copyOf(sources);
  }

  /** Source, result and detail of one of the four results. */
  public record SourceSummary(String source, String result, String detail) {}
}
