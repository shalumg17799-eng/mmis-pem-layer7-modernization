package gov.medicaid.pem.screening.application;

import gov.medicaid.pem.screening.domain.SourceResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One screening of one provider with its four source results (contract Screening). */
public record Screening(
    UUID screeningId,
    String npi,
    String reason,
    String status,
    String result,
    List<SourceResult> results,
    Instant startedAt,
    Instant completedAt) {

  public Screening {
    results = List.copyOf(results);
  }
}
