package gov.medicaid.pem.enrollment.application;

import gov.medicaid.pem.enrollment.domain.ApplicationValidator;
import gov.medicaid.pem.enrollment.domain.OwnNpiRule;
import gov.medicaid.pem.enrollment.domain.ProviderTypeMapping;
import gov.medicaid.pem.enrollment.domain.Validation;
import gov.medicaid.pem.kit.security.CurrentUser;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Submits an application (PRVENR01 replaced for the portal): validate, store once, announce once
 * (D-001). A VALIDATED application is announced as ApplicationValidated so that provider-service
 * registers a PENDING provider (D-019); a REJECTED one is announced as ApplicationRejected only
 * when it has a 10-digit NPI to use as subject and key.
 */
@Service
public class SubmitApplication {

  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(SubmitApplication.class);

  static final String VALIDATED = "VALIDATED";
  static final String REJECTED = "REJECTED";

  private final ApplicationStore store;
  private final EventOutbox outbox;
  private final Clock clock;

  public SubmitApplication(ApplicationStore store, EventOutbox outbox, Clock clock) {
    this.store = store;
    this.outbox = outbox;
    this.clock = clock;
  }

  @Transactional
  public StoredApplication submit(NewApplication in, CurrentUser user) {
    ProviderTypeMapping types = ProviderTypeMapping.of(store.providerTypeMappings());
    if (!user.isStaff()
        && !OwnNpiRule.allows(
            types.toLegacy(in.providerType()).orElse(null), in.npi(), user.npi())) {
      // Q-095: refused before anything is stored or announced; the NPI is not echoed back
      throw new AccessDeniedException(
          "An individual application can only be submitted under your own NPI.");
    }
    return store(in, "PORTAL", user.name(), id -> {});
  }

  /**
   * MAM-56: an application read from the APPINPUT file gets the same validation, storage and events
   * as an online one, with source LEGACY_FILE; {@code attach} ties it to its intake run in the same
   * transaction.
   */
  @Transactional
  public StoredApplication fromFile(NewApplication in, String submittedBy, Consumer<UUID> attach) {
    return store(in, "LEGACY_FILE", submittedBy, attach);
  }

  private StoredApplication store(
      NewApplication in, String source, String submittedBy, Consumer<UUID> attach) {
    ProviderTypeMapping types = ProviderTypeMapping.of(store.providerTypeMappings());
    boolean npi = ApplicationValidator.isNpi(in.npi());
    Validation v =
        ApplicationValidator.validate(
            in.npi(), in.providerType(), in.taxonomy(), types, npi && store.hasValidated(in.npi()));
    StoredApplication app =
        new StoredApplication(
            UUID.randomUUID(),
            v.valid() ? VALIDATED : REJECTED,
            v.errors(),
            in.npi(),
            in.medicaidId(),
            v.providerType(),
            in.taxonomy(),
            in.individual(),
            in.organization(),
            source,
            clock.instant());
    store.save(app, submittedBy);
    attach.accept(app.applicationId());
    LOG.info("Application {} for NPI {}: {}", app.applicationId(), app.npi(), app.status());
    if (v.valid()) {
      outbox.enqueue(
          "ApplicationValidated",
          app.npi(),
          new ApplicationValidatedData(
              app.applicationId(),
              app.npi(),
              app.medicaidId(),
              app.providerType(),
              app.taxonomy(),
              app.individual(),
              app.organization()));
    } else if (npi) {
      outbox.enqueue(
          "ApplicationRejected",
          app.npi(),
          new ApplicationRejectedData(
              app.applicationId(),
              app.npi(),
              app.errors().stream()
                  .map(e -> new ApplicationRejectedData.Error(e.code().name(), e.field()))
                  .toList()));
    }
    return app;
  }
}
