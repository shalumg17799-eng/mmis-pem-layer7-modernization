package gov.medicaid.pem.provider.application;

import gov.medicaid.pem.kit.security.CurrentUser;
import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.provider.domain.Approval;
import gov.medicaid.pem.provider.domain.Decision;
import gov.medicaid.pem.provider.domain.DecisionRefused;
import gov.medicaid.pem.provider.domain.Provider;
import gov.medicaid.pem.provider.domain.ProviderRegistration;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * decideProvider (MAM-27): the analyst's APPROVE or DENY is applied to the record and announced
 * once as ProviderStatusChanged, in one transaction. A refused decision changes nothing and
 * announces nothing (D-024).
 */
@Service
public class DecideProvider {

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

  public DecideProvider(
      ProviderStore store, DecisionWriter writer, EventOutbox outbox, Clock clock) {
    this.store = store;
    this.writer = writer;
    this.outbox = outbox;
    this.clock = clock;
  }

  @Transactional
  public Provider decide(String npi, Decision decision, String reason, CurrentUser actor) {
    Provider before =
        store
            .findByNpi(npi)
            .orElseThrow(() -> ApiException.notFound("No provider found for NPI " + npi));
    Provider after;
    try {
      after = Approval.decide(before, decision, LocalDate.now(clock));
    } catch (DecisionRefused e) {
      throw new ApiException(
          e.conflict() ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_ENTITY,
          null,
          e.getMessage(),
          List.of());
    }
    writer.save(after);
    audit.record(
        npi,
        actor.name(),
        decision == Decision.APPROVE ? "APPROVED" : "DENIED",
        AuditTrail.snapshot(before),
        AuditTrail.snapshot(
            store.findByNpi(npi).orElse(after),
            "decision",
            AuditTrail.details("decision", decision.name(), "reason", reason)));
    outbox.enqueue(
        "ProviderStatusChanged",
        npi,
        new ProviderStatusChangedData(
            npi,
            before.enrollStatus().code(),
            after.enrollStatus().code(),
            reason,
            actor.name(),
            after.revalidationDueDate(),
            after.providerType().code(),
            ProviderRegistration.displayName(after),
            after.taxonomy(),
            after.medicaidId()));
    return after;
  }
}
