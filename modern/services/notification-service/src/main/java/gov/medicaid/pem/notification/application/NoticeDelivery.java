package gov.medicaid.pem.notification.application;

import gov.medicaid.pem.notification.domain.NoticeRules;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The one place where a notice is delivered, for every notice family (MAM-58 sanction notices,
 * MAM-59 status, rejection and revalidation notices), so that they cannot drift apart. The
 * template's email goes to the provider's mailbox (D-012), the notice is stored as SENT and
 * NoticeSent is announced. A template that is not found (the legacy 'NOTICE PGM NOT FOUND') or an
 * email that cannot be sent is logged as one warning and stored as a FAILED notice; it is never
 * thrown, so the processing goes on and nothing is undone (D-024). A failure of the stores after
 * the email was sent is thrown, so the caller's transaction rolls back and the event is retried.
 */
final class NoticeDelivery {

  private static final Logger LOG = LoggerFactory.getLogger(NoticeDelivery.class);

  private final NoticePorts.Templates templates;
  private final NoticePorts.Mailer mailer;
  private final NoticePorts.NoticeStore store;
  private final NoticePorts.FailedNotices failedNotices;
  private final NoticePorts.EventOutbox outbox;
  private final Clock clock;

  NoticeDelivery(
      NoticePorts.Templates templates,
      NoticePorts.Mailer mailer,
      NoticePorts.NoticeStore store,
      NoticePorts.FailedNotices failedNotices,
      NoticePorts.EventOutbox outbox,
      Clock clock) {
    this.templates = templates;
    this.mailer = mailer;
    this.store = store;
    this.failedNotices = failedNotices;
    this.outbox = outbox;
    this.clock = clock;
  }

  /** The notice that was sent, or empty when it could not be sent (a FAILED notice is stored). */
  Optional<Notice> deliver(String npi, String templateCode, UUID eventId) {
    String to = NoticeRules.recipient(npi);
    Optional<Template> template = templates.find(templateCode);
    if (template.isEmpty()) {
      return failed(npi, templateCode, to, eventId, "NOTICE TEMPLATE NOT FOUND " + templateCode);
    }
    try {
      mailer.send(to, template.get().subject(), template.get().bodyFor(npi));
    } catch (RuntimeException e) {
      return failed(
          npi, templateCode, to, eventId, e.getMessage() == null ? e.toString() : e.getMessage());
    }
    Notice notice = new Notice(UUID.randomUUID(), npi, templateCode, to, eventId, clock.instant());
    store.save(notice);
    outbox.enqueue("NoticeSent", npi, new NoticeSentData(notice.noticeId(), npi, templateCode));
    return Optional.of(notice);
  }

  private Optional<Notice> failed(
      String npi, String templateCode, String to, UUID eventId, String cause) {
    LOG.warn(
        "NOTICE {} FOR NPI {} STATUS FAILED (event {}), cause: {}",
        templateCode,
        npi,
        eventId,
        cause);
    failedNotices.save(new Notice(UUID.randomUUID(), npi, templateCode, to, eventId, null), cause);
    return Optional.empty();
  }
}
