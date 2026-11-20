package gov.medicaid.pem.provider.application;

import gov.medicaid.pem.kit.security.CurrentUser;
import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.provider.domain.EnrollStatus;
import gov.medicaid.pem.provider.domain.Provider;
import gov.medicaid.pem.provider.domain.ProviderRegistration;
import gov.medicaid.pem.provider.domain.StatusAction;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * changeProviderStatus (MAM-33): PRVSAN01 APPLY-SANCTION through provider-service's API (D-013,
 * D-014). The new status is saved and announced once as ProviderStatusChanged in one transaction,
 * and a termination is counted (WS-TERM-COUNT). An unknown action or an incomplete request is a 422
 * and an unknown NPI a 404; neither changes or announces anything (D-024, D-026).
 */
@Service
public class ChangeProviderStatus {

  static final int REASON_MAX = 60;

  private final ProviderStore store;
  private final DecisionWriter writer;
  private final EventOutbox outbox;
  private final StatusChangeMetrics metrics;

  private AuditTrail audit = AuditTrail.NONE;

  /** MAM-40: the provider history; unit tests that do not wire one get none. */
  @Autowired(required = false)
  void setAuditTrail(AuditTrail audit) {
    this.audit = audit;
  }

  public ChangeProviderStatus(
      ProviderStore store, DecisionWriter writer, EventOutbox outbox, StatusChangeMetrics metrics) {
    this.store = store;
    this.writer = writer;
    this.outbox = outbox;
    this.metrics = metrics;
  }

  @Transactional
  public Provider change(String npi, StatusChangeCommand command, CurrentUser actor) {
    StatusAction action =
        StatusAction.fromName(command.action())
            .orElseThrow(
                () -> unprocessable("action must be one of TERMINATE, SUSPEND or REINSTATE."));
    if (command.effectiveDate() == null) {
      throw unprocessable("effectiveDate is required.");
    }
    if (command.reason() != null && command.reason().length() > REASON_MAX) {
      throw unprocessable("reason must be at most " + REASON_MAX + " characters.");
    }
    Provider before =
        store
            .findByNpi(npi)
            .orElseThrow(() -> ApiException.notFound("No provider found for NPI " + npi));
    Provider after = action.applyTo(before);
    writer.save(after);
    audit.record(
        npi,
        actor.name(),
        switch (action) {
          case TERMINATE -> "TERMINATED";
          case SUSPEND -> "SUSPENDED";
          case REINSTATE -> "REINSTATED";
        },
        AuditTrail.snapshot(before),
        AuditTrail.snapshot(
            store.findByNpi(npi).orElse(after),
            "statusChange",
            AuditTrail.details(
                "action",
                action.name(),
                "reason",
                command.reason(),
                "effectiveDate",
                command.effectiveDate(),
                "sanctionId",
                command.sanctionId())));
    outbox.enqueue(
        "ProviderStatusChanged",
        npi,
        new ProviderStatusChangedData(
            npi,
            before.enrollStatus().code(),
            after.enrollStatus().code(),
            command.reason(),
            actor.name(),
            after.revalidationDueDate(),
            after.providerType().code(),
            ProviderRegistration.displayName(after),
            after.taxonomy(),
            after.medicaidId()));
    if (after.enrollStatus() == EnrollStatus.TERMINATED) {
      metrics.terminated();
    }
    return after;
  }

  private static ApiException unprocessable(String detail) {
    return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, null, detail, List.of());
  }
}
