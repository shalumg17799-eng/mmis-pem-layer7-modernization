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
 * Sends the sanction notice of a SanctionApplied event (MAM-58), replacing PRVSAN01 GENERATE-NOTICE
 * and the missing PRVNT* programs (D-012): T gives TRM, S gives SUS, R gives RST. Any other action
 * is refused and logged as an error, never sent as GEN (D-026, Q-A10). A send that fails, or a
 * template that is not found (the legacy 'NOTICE PGM NOT FOUND'), is logged as a warning and stored
 * as a FAILED notice; it is never thrown, so the processing goes on and nothing is undone (D-024).
 */
@Service
public class SendSanctionNotice {

  private static final Logger LOG = LoggerFactory.getLogger(SendSanctionNotice.class);

  /** What happened to the event. */
  public enum Outcome {
    SENT,
    FAILED,
    REFUSED
  }

  private final NoticePorts.Templates templates;
  private final NoticePorts.Mailer mailer;
  private final NoticePorts.NoticeStore store;
  private final NoticePorts.FailedNotices failedNotices;
  private final NoticePorts.EventOutbox outbox;
  private final Clock clock;

  public SendSanctionNotice(
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

  @Transactional
  public Outcome send(String npi, String action, UUID eventId) {
    Optional<String> code = NoticeRules.forSanction(action);
    if (code.isEmpty()) {
      LOG.error(
          "SanctionApplied {} for NPI {} refused: unknown action {} (allowed T, S, R); no notice sent",
          eventId,
          npi,
          action);
      return Outcome.REFUSED;
    }
    String template = code.get();
    String to = NoticeRules.recipient(npi);
    Optional<Template> t = templates.find(template);
    if (t.isEmpty()) {
      return failed(npi, template, to, eventId, "NOTICE TEMPLATE NOT FOUND " + template);
    }
    try {
      mailer.send(to, t.get().subject(), t.get().bodyFor(npi));
    } catch (RuntimeException e) {
      return failed(
          npi, template, to, eventId, e.getMessage() == null ? e.toString() : e.getMessage());
    }
    Notice n = new Notice(UUID.randomUUID(), npi, template, to, eventId, clock.instant());
    store.save(n);
    outbox.enqueue("NoticeSent", npi, new NoticeSentData(n.noticeId(), npi, template));
    return Outcome.SENT;
  }

  private Outcome failed(String npi, String template, String to, UUID eventId, String cause) {
    LOG.warn(
        "NOTICE {} FOR NPI {} STATUS FAILED (event {}), cause: {}", template, npi, eventId, cause);
    failedNotices.save(new Notice(UUID.randomUUID(), npi, template, to, eventId, null), cause);
    return Outcome.FAILED;
  }
}
