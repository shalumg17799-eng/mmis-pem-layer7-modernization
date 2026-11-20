package gov.medicaid.pem.notification.application;

import gov.medicaid.pem.notification.domain.NoticeRules;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends one notice (D-012): the template's email goes to the provider's mailbox, the notice is
 * stored and NoticeSent is announced, in the event's transaction. A template that is not set up
 * sends nothing and is logged.
 */
@Service
public class SendNotice {

  private static final Logger LOG = LoggerFactory.getLogger(SendNotice.class);

  private final NoticePorts.Templates templates;
  private final NoticePorts.Mailer mailer;
  private final NoticePorts.NoticeStore store;
  private final NoticePorts.EventOutbox outbox;
  private final Clock clock;

  public SendNotice(
      NoticePorts.Templates templates,
      NoticePorts.Mailer mailer,
      NoticePorts.NoticeStore store,
      NoticePorts.EventOutbox outbox,
      Clock clock) {
    this.templates = templates;
    this.mailer = mailer;
    this.store = store;
    this.outbox = outbox;
    this.clock = clock;
  }

  @Transactional
  public Optional<Notice> send(String npi, String templateCode, UUID eventId) {
    Optional<Template> t = templates.find(templateCode);
    if (t.isEmpty()) {
      LOG.warn(
          "no notice template {}: nothing sent for NPI {} (event {})", templateCode, npi, eventId);
      return Optional.empty();
    }
    String to = NoticeRules.recipient(npi);
    mailer.send(to, t.get().subject(), t.get().bodyFor(npi));
    Notice n = new Notice(UUID.randomUUID(), npi, templateCode, to, eventId, clock.instant());
    store.save(n);
    outbox.enqueue("NoticeSent", npi, new NoticeSentData(n.noticeId(), npi, templateCode));
    return Optional.of(n);
  }
}
