package gov.medicaid.pem.provider.application;

import gov.medicaid.pem.provider.domain.Provider;
import gov.medicaid.pem.provider.domain.ProviderRegistration;
import gov.medicaid.pem.provider.domain.Revalidation;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A passed revalidation re-screening (ScreeningCompleted, reason REVALIDATION, result PASS) sets
 * the next due date from the service clock and announces it as ProviderStatusChanged, so that
 * revalidation-service can follow it (MAM-30, D-003, Q-A4).
 */
@Service
public class CompleteRevalidation {

  private static final Logger LOG = LoggerFactory.getLogger(CompleteRevalidation.class);
  static final String ACTOR = "screening-service";
  static final String REASON = "Revalidation passed";

  private final ProviderStore store;
  private final DecisionWriter writer;
  private final EventOutbox outbox;
  private final Clock clock;

  private AuditTrail audit = AuditTrail.NONE;

  /** MAM-40: the provider history; unit tests that do not wire one get none. */
  @Autowired(required = false)
  void setAuditTrail(AuditTrail audit) {
    this.audit = audit;
  }

  public CompleteRevalidation(
      ProviderStore store, DecisionWriter writer, EventOutbox outbox, Clock clock) {
    this.store = store;
    this.writer = writer;
    this.outbox = outbox;
    this.clock = clock;
  }

  /** False, and nothing changed, when the NPI is not on file. */
  @Transactional
  public boolean passed(String npi) {
    var found = store.findByNpi(npi);
    if (found.isEmpty()) {
      LOG.warn("revalidation result for NPI {} not applied: no such provider", npi);
      return false;
    }
    Provider before = found.get();
    Provider after = Revalidation.passed(before, LocalDate.now(clock));
    writer.save(after);
    audit.record(
        npi,
        ACTOR,
        "REVALIDATION_PASSED",
        AuditTrail.snapshot(before),
        AuditTrail.snapshot(store.findByNpi(npi).orElse(after)));
    outbox.enqueue(
        "ProviderStatusChanged",
        npi,
        new ProviderStatusChangedData(
            npi,
            before.enrollStatus().code(),
            after.enrollStatus().code(),
            REASON,
            ACTOR,
            after.revalidationDueDate(),
            after.providerType().code(),
            ProviderRegistration.displayName(after),
            after.taxonomy(),
            after.medicaidId()));
    LOG.info("Provider {} revalidated: next due date {}", npi, after.revalidationDueDate());
    return true;
  }
}
