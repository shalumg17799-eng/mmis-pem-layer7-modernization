package gov.medicaid.pem.provider.application;

import gov.medicaid.pem.provider.domain.Provider;
import gov.medicaid.pem.provider.domain.ProviderApplication;
import gov.medicaid.pem.provider.domain.ProviderRegistration;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers a validated application as a PENDING, UNSCREENED provider and announces
 * ProviderRegistered (MAM-17). An NPI that is already on file is never overwritten: it is logged
 * and counted, as the legacy duplicate-key branch did (PRVENR01 163-166), and nothing is announced.
 */
@Service
public class RegisterProvider {

  private static final Logger LOG = LoggerFactory.getLogger(RegisterProvider.class);
  static final String ACTOR = "enrollment-service";

  private final ProviderRegistry registry;
  private final EventOutbox outbox;
  private final RegistrationMetrics metrics;
  private final Clock clock;

  private AuditTrail audit = AuditTrail.NONE;

  /** MAM-40: the provider history; unit tests that do not wire one get none. */
  @Autowired(required = false)
  void setAuditTrail(AuditTrail audit) {
    this.audit = audit;
  }

  public RegisterProvider(
      ProviderRegistry registry, EventOutbox outbox, RegistrationMetrics metrics, Clock clock) {
    this.registry = registry;
    this.outbox = outbox;
    this.metrics = metrics;
    this.clock = clock;
  }

  @Transactional
  public RegistrationOutcome register(ProviderApplication application) {
    return register(application, ACTOR);
  }

  /** MAM-40: the actor of the history entry is the service that produced the event. */
  @Transactional
  public RegistrationOutcome register(ProviderApplication application, String actor) {
    if (registry.exists(application.npi())) {
      LOG.warn(
          "DUPLICATE NPI {}: provider already registered, application not applied",
          application.npi());
      metrics.duplicateSkipped();
      return RegistrationOutcome.DUPLICATE;
    }
    Provider p = ProviderRegistration.register(application, LocalDate.now(clock));
    registry.add(p);
    LOG.info("Provider {} registered as PENDING, UNSCREENED", p.npi());
    audit.record(p.npi(), actor, "REGISTERED", null, AuditTrail.snapshot(p));
    outbox.enqueue(
        "ProviderRegistered",
        p.npi(),
        new ProviderRegisteredData(
            p.npi(),
            p.medicaidId(),
            p.providerType().code(),
            p.taxonomy(),
            p.enrollStatus().code(),
            p.screenFlag().code(),
            ProviderRegistration.displayName(p)));
    return RegistrationOutcome.REGISTERED;
  }
}
