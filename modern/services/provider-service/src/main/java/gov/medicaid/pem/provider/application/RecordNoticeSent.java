package gov.medicaid.pem.provider.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NoticeSent (MAM-40 AC-4, events.yaml, D-012): a notice sent to a provider is added to its history
 * as a reference (notice id and template); the provider's own fields and version do not change.
 */
@Service
public class RecordNoticeSent {

  private static final Logger LOG = LoggerFactory.getLogger(RecordNoticeSent.class);
  static final String ACTION = "NOTICE_SENT";

  private final ProviderStore store;
  private final AuditTrail audit;

  public RecordNoticeSent(ProviderStore store, AuditTrail audit) {
    this.store = store;
    this.audit = audit;
  }

  /** False, and nothing recorded, when the NPI is not on file. */
  @Transactional
  public boolean noticeSent(String npi, String noticeId, String template, String actor) {
    if (store.findByNpi(npi).isEmpty()) {
      LOG.warn("notice {} for NPI {} not recorded: no such provider", noticeId, npi);
      return false;
    }
    audit.record(
        npi,
        actor,
        ACTION,
        null,
        AuditTrail.details(
            "notice", AuditTrail.details("noticeId", noticeId, "template", template)));
    return true;
  }
}
