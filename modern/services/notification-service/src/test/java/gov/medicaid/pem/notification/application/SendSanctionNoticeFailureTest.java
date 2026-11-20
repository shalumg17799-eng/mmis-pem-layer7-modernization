package gov.medicaid.pem.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import gov.medicaid.pem.notification.LogCapture;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mail.MailSendException;

/**
 * MAM-58 Lane 3, unit level (fake ports, fixed Clock 2026-11-20): the exact words of the WARN and
 * ERROR lines of D-024 and D-026, the cause that is recorded, and which failures are recorded as
 * FAILED (the mail server, a missing template) and which are thrown so that the surrounding
 * transaction rolls back (the stores after the email was sent).
 */
class SendSanctionNoticeFailureTest {

  static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
  static final String NPI = "1003000111";

  record Mail(String to, String subject, String body) {}

  record Failed(Notice notice, String cause) {}

  record Sent(String type, String subject, Object data) {}

  final List<String> asked = new ArrayList<>();
  final List<Mail> mails = new ArrayList<>();
  final List<Notice> stored = new ArrayList<>();
  final List<Failed> failed = new ArrayList<>();
  final List<Sent> sent = new ArrayList<>();
  final Map<String, Template> templates = new HashMap<>();
  RuntimeException mailFailure;
  RuntimeException storeFailure;
  RuntimeException outboxFailure;

  {
    templates.put("TRM", new Template("TRM", "Termination notice", "NPI {npi} is terminated."));
    templates.put("SUS", new Template("SUS", "Suspension notice", "NPI {npi} is suspended."));
    templates.put("RST", new Template("RST", "Reinstatement notice", "NPI {npi} is reinstated."));
  }

  final SendSanctionNotice notices =
      new SendSanctionNotice(
          code -> {
            asked.add(code);
            return Optional.ofNullable(templates.get(code));
          },
          (to, subject, body) -> {
            if (mailFailure != null) {
              throw mailFailure;
            }
            mails.add(new Mail(to, subject, body));
          },
          n -> {
            if (storeFailure != null) {
              throw storeFailure;
            }
            stored.add(n);
          },
          (notice, cause) -> failed.add(new Failed(notice, cause)),
          (type, subject, data) -> {
            if (outboxFailure != null) {
              throw outboxFailure;
            }
            sent.add(new Sent(type, subject, data));
          },
          Clock.fixed(NOW, ZoneOffset.UTC));

  LogCapture log;

  @BeforeEach
  void capture() {
    log = LogCapture.open();
  }

  @AfterEach
  void release() {
    log.close();
  }

  @Test
  @DisplayName(
      "[MAM-58 AC-6] the cause of a failed send is the message of the exception, and nothing else")
  void causeIsTheMessage() {
    mailFailure = new IllegalStateException("Mail server connection failed");

    notices.send(NPI, "S", UUID.randomUUID());

    assertThat(failed).hasSize(1);
    assertThat(failed.get(0).cause()).isEqualTo("Mail server connection failed");
  }

  @Test
  @DisplayName(
      "[MAM-58 AC-6] the cause of a failure whose exception has no message is the exception text")
  void causeWithoutMessage() {
    mailFailure = new IllegalStateException();

    notices.send(NPI, "S", UUID.randomUUID());

    assertThat(failed.get(0).cause()).isEqualTo("java.lang.IllegalStateException");
  }

  @Test
  @DisplayName(
      "[MAM-58 AC-6] a failed send is one WARN line with template, NPI, status FAILED, event id and"
          + " cause, in the words of the legacy message")
  void warningWords() {
    UUID event = UUID.randomUUID();
    mailFailure = new MailSendException("Mail server connection failed");

    notices.send(NPI, "S", event);

    List<ILoggingEvent> warnings =
        log.events().stream().filter(e -> e.getLevel() == Level.WARN).toList();
    assertThat(warnings).hasSize(1);
    assertThat(warnings.get(0).getFormattedMessage())
        .isEqualTo(
            "NOTICE SUS FOR NPI "
                + NPI
                + " STATUS FAILED (event "
                + event
                + "), cause: Mail server connection failed");
  }

  @ParameterizedTest(
      name =
          "[MAM-58 AC-7] action {0}: a missing {1} template is a FAILED notice with the cause"
              + " NOTICE TEMPLATE NOT FOUND {1}, logged as a WARN line, and nothing is emailed")
  @CsvSource({"T,TRM", "S,SUS", "R,RST"})
  void missingTemplateWords(String action, String template) {
    UUID event = UUID.randomUUID();
    templates.remove(template);

    SendSanctionNotice.Outcome outcome = notices.send(NPI, action, event);

    assertThat(outcome).isEqualTo(SendSanctionNotice.Outcome.FAILED);
    assertThat(asked).containsExactly(template);
    assertThat(failed).hasSize(1);
    assertThat(failed.get(0).notice().templateCode()).isEqualTo(template);
    assertThat(failed.get(0).cause()).isEqualTo("NOTICE TEMPLATE NOT FOUND " + template);
    assertThat(log.messages(Level.WARN))
        .containsExactly(
            "NOTICE "
                + template
                + " FOR NPI "
                + NPI
                + " STATUS FAILED (event "
                + event
                + "), cause: NOTICE TEMPLATE NOT FOUND "
                + template);
    assertThat(mails).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-58 AC-4] an action outside T, S and R is one ERROR line naming the event id, the NPI"
          + " and the action; no template is looked up and nothing is sent")
  void refusalWords() {
    UUID event = UUID.randomUUID();

    SendSanctionNotice.Outcome outcome = notices.send(NPI, "X", event);

    assertThat(outcome).isEqualTo(SendSanctionNotice.Outcome.REFUSED);
    List<String> errors = log.messages(Level.ERROR);
    assertThat(errors).hasSize(1);
    assertThat(errors.get(0))
        .contains(event.toString())
        .contains("NPI " + NPI)
        .contains("unknown action X")
        .contains("no notice sent");
    assertThat(log.messages(Level.WARN)).isEmpty();
    assertThat(asked).isEmpty();
    assertThat(mails).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-58 AC-6] a failed notice is addressed to the provider's mailbox, has its own id and"
          + " the event id, and no sent time")
  void failedNoticeFields() {
    UUID event = UUID.randomUUID();
    mailFailure = new IllegalStateException("down");

    notices.send(NPI, "T", event);

    Notice n = failed.get(0).notice();
    assertThat(n.noticeId()).isNotNull();
    assertThat(n.npi()).isEqualTo(NPI);
    assertThat(n.templateCode()).isEqualTo("TRM");
    assertThat(n.recipient()).isEqualTo("1003000111@providers.pem.local");
    assertThat(n.eventId()).isEqualTo(event);
    assertThat(n.sentAt()).isNull();
  }

  @Test
  @DisplayName(
      "[MAM-58 AC-6] the processing goes on: after a failed send the next event is sent and"
          + " announced")
  void nextEventIsProcessed() {
    mailFailure = new IllegalStateException("down");
    notices.send("1003000111", "S", UUID.randomUUID());
    mailFailure = null;

    SendSanctionNotice.Outcome second = notices.send("1003000444", "T", UUID.randomUUID());

    assertThat(second).isEqualTo(SendSanctionNotice.Outcome.SENT);
    assertThat(failed).hasSize(1);
    assertThat(mails).extracting(Mail::to).containsExactly("1003000444@providers.pem.local");
    assertThat(sent).hasSize(1);
  }

  @Test
  @DisplayName(
      "[MAM-58 AC-8] a failure of the notice store after the email was sent is thrown, so the"
          + " transaction rolls back; it is not recorded as a FAILED notice (lesson c)")
  void storeFailureIsThrown() {
    storeFailure = new IllegalStateException("database down");

    assertThatThrownBy(() -> notices.send(NPI, "S", UUID.randomUUID())).isSameAs(storeFailure);

    assertThat(failed).isEmpty();
    assertThat(sent).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-58 AC-8] a failure of the outbox after the email was sent is thrown, so the"
          + " transaction rolls back; it is not recorded as a FAILED notice (lesson c)")
  void outboxFailureIsThrown() {
    outboxFailure = new IllegalStateException("outbox down");

    assertThatThrownBy(() -> notices.send(NPI, "S", UUID.randomUUID())).isSameAs(outboxFailure);

    assertThat(failed).isEmpty();
  }

  @Test
  @DisplayName("[MAM-58 AC-1] the sent notice is stored with the time of the clock and announced")
  void sentNoticeIsStoredAndAnnounced() {
    UUID event = UUID.randomUUID();

    SendSanctionNotice.Outcome outcome = notices.send(NPI, "R", event);

    assertThat(outcome).isEqualTo(SendSanctionNotice.Outcome.SENT);
    assertThat(stored).hasSize(1);
    assertThat(stored.get(0).sentAt()).isEqualTo(NOW);
    assertThat(stored.get(0).eventId()).isEqualTo(event);
    assertThat(sent)
        .containsExactly(
            new Sent("NoticeSent", NPI, new NoticeSentData(stored.get(0).noticeId(), NPI, "RST")));
    assertThat(log.messages(Level.WARN)).isEmpty();
    assertThat(log.messages(Level.ERROR)).isEmpty();
  }
}
