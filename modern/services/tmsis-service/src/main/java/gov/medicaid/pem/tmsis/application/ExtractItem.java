package gov.medicaid.pem.tmsis.application;

import gov.medicaid.pem.tmsis.domain.FutureEnrolment;
import gov.medicaid.pem.tmsis.domain.TmsisRecord;
import java.time.LocalDate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One reported provider of a run: its TMSISOUT record and whether its enrolment year is later than
 * the business year (MAM-78). Only ACTIVE providers become items (R-PRVTMS01-ADD-04), so only they
 * are checked; a flagged provider is logged with the legacy DISPLAY text and still reported.
 */
public record ExtractItem(TmsisRecord record, boolean futureEnrolment) {

  private static final Logger LOG = LoggerFactory.getLogger(ExtractItem.class);

  public static Optional<ExtractItem> of(ProviderRow p, LocalDate businessDate, String period) {
    return TmsisRecord.of(p.medicaidId(), p.npi(), p.enrollStatus(), period)
        .map(
            r -> {
              boolean future = FutureEnrolment.flagged(p.effectiveDate(), businessDate);
              if (future) {
                LOG.warn("PRVTMS01: FUTURE ENROLLMENT? NPI {}", p.npi());
              }
              return new ExtractItem(r, future);
            });
  }
}
