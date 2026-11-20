package gov.medicaid.pem.screening.application;

import java.util.List;
import java.util.UUID;

/** Data of the ScreeningCompleted event (contracts/asyncapi/events.yaml#ScreeningCompletedData). */
public record ScreeningCompletedData(
    UUID screeningId,
    String npi,
    String reason,
    String result,
    String taxonomy,
    List<SourceSummary> sources) {

  public ScreeningCompletedData {
    sources = List.copyOf(sources);
  }

  /** Source, result and detail of one of the four results. */
  public record SourceSummary(String source, String result, String detail) {}
}
