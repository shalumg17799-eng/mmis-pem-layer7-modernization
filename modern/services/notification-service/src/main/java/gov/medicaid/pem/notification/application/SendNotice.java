package gov.medicaid.pem.notification.application;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends one notice of MAM-59 (APPROVED, DENIED, REJECTED, REVALIDATION_DUE) in the event's
 * transaction (D-012): the template's email goes to the provider's mailbox, the notice is stored
 * and NoticeSent is announced. A template that is not set up, or an email that cannot be sent, is
 * logged and stored as a FAILED notice and never thrown, exactly as for the sanction notices
 * (D-024); see {@link NoticeDelivery}.
 */
@Service
public class SendNotice {

  private final NoticeDelivery delivery;

  public SendNotice(
      NoticePorts.Templates templates,
      NoticePorts.Mailer mailer,
      NoticePorts.NoticeStore store,
      NoticePorts.FailedNotices failedNotices,
      NoticePorts.EventOutbox outbox,
      Clock clock) {
    this.delivery = new NoticeDelivery(templates, mailer, store, failedNotices, outbox, clock);
  }

  /** The notice that was sent; empty when it could not be sent (a FAILED notice is stored). */
  @Transactional
  public Optional<Notice> send(String npi, String templateCode, UUID eventId) {
    return delivery.deliver(npi, templateCode, eventId);
  }
}
