package gov.medicaid.pem.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mail.MailSendException;

/**
 * Code review of notification-service, 2026-11-20, MUST-FIX 1. The failed-send and missing-template
 * handling of D-024 (MAM-58 AC-6 and AC-7) applies to the APPROVED, DENIED, REJECTED and
 * REVALIDATION_DUE notices of MAM-59 exactly as to TRM, SUS and RST: MAM-59 puts "the same handling
 * applies to these notice types" in its out-of-scope note, and MAM-60 AC-5 requires that a notice
 * that could not be sent shows as FAILED. Unit level (fake ports, fixed Clock 2026-11-20): the
 * exact words of the WARN line, the cause that is recorded, which failures are recorded as FAILED
 * (the mail server, a missing template) and which are thrown so that the surrounding transaction
 * rolls back (the stores after the email was sent). The words are the ones of {@code
 * SendSanctionNoticeFailureTest}, so the two notice families cannot drift apart.
 *
 * <p>Assumed interface: {@code SendNotice(templates, mailer, store, failedNotices, outbox, clock)}
 * in the order of {@code SendSanctionNotice}, and {@code send(npi, templateCode, eventId)} keeps
 * its signature and returns the notice that was sent, empty when nothing was sent.
 */
class SendNoticeFailureTest {

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
    templates.put(
        "APPROVED", new Template("APPROVED", "Enrolment approved", "NPI {npi} is approved."));
    templates.put("DENIED", new Template("DENIED", "Enrolment denied", "NPI {npi} is denied."));
    templates.put(
        "REJECTED", new Template("REJECTED", "Application rejected", "NPI {npi} was rejected."));
    templates.put(
        "REVALIDATION_DUE", new Template("REVALIDATION_DUE", "Revalidation due", "NPI {npi}."));
  }

  final SendNotice notices =
      new SendNotice(
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

  /** The notice code and the MAM-59 acceptance criterion that sends it. */
  static Stream<Arguments> noticeTypes() {
    return Stream.of(
        Arguments.of("APPROVED", "AC-1"),
        Arguments.of("DENIED", "AC-2"),
        Arguments.of("REJECTED", "AC-4"),
        Arguments.of("REVALIDATION_DUE", "AC-6"));
  }

  static String warning(String template, UUID event, String cause) {
    return "NOTICE "
        + template
        + " FOR NPI "
        + NPI
        + " STATUS FAILED (event "
        + event
        + "), cause: "
        + cause;
  }

  // ---- a mail server that fails
  // ---------------------------------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-59 {1}] the {0} notice: a mail server that fails returns nothing and is not thrown,"
              + " so the processing goes on (D-024)")
  @MethodSource("noticeTypes")
  void failedSendIsNotThrown(String code, String ac) {
    mailFailure = new MailSendException("Mail server connection failed");

    Optional<Notice> result = notices.send(NPI, code, UUID.randomUUID());

    assertThat(result).isEmpty();
  }

  @ParameterizedTest(
      name =
          "[MAM-59 {1}] the {0} notice: a failed send is a FAILED notice addressed to the provider's"
              + " mailbox, with its own id, the event id and no sent time")
  @MethodSource("noticeTypes")
  void failedNoticeFields(String code, String ac) {
    UUID event = UUID.randomUUID();
    mailFailure = new IllegalStateException("down");

    notices.send(NPI, code, event);

    assertThat(failed).hasSize(1);
    Notice n = failed.get(0).notice();
    assertThat(n.noticeId()).isNotNull();
    assertThat(n.npi()).isEqualTo(NPI);
    assertThat(n.templateCode()).isEqualTo(code);
    assertThat(n.recipient()).isEqualTo("1003000111@providers.pem.local");
    assertThat(n.eventId()).isEqualTo(event);
    assertThat(n.sentAt()).isNull();
  }

  @ParameterizedTest(
      name =
          "[MAM-59 {1}] the {0} notice: the cause of a failed send is the message of the exception,"
              + " and nothing else")
  @MethodSource("noticeTypes")
  void causeIsTheMessage(String code, String ac) {
    mailFailure = new IllegalStateException("Mail server connection failed");

    notices.send(NPI, code, UUID.randomUUID());

    assertThat(failed).hasSize(1);
    assertThat(failed.get(0).cause()).isEqualTo("Mail server connection failed");
  }

  @Test
  @DisplayName(
      "[MAM-59 AC-1] the cause of a failure whose exception has no message is the exception text")
  void causeWithoutMessage() {
    mailFailure = new IllegalStateException();

    notices.send(NPI, "APPROVED", UUID.randomUUID());

    assertThat(failed).hasSize(1);
    assertThat(failed.get(0).cause()).isEqualTo("java.lang.IllegalStateException");
  }

  @ParameterizedTest(
      name =
          "[MAM-59 {1}] the {0} notice: a failed send is one WARN line with template, NPI, status"
              + " FAILED, event id and cause, in the words of the sanction notices (D-024)")
  @MethodSource("noticeTypes")
  void warningWords(String code, String ac) {
    UUID event = UUID.randomUUID();
    mailFailure = new MailSendException("Mail server connection failed");

    notices.send(NPI, code, event);

    assertThat(log.messages(Level.WARN))
        .containsExactly(warning(code, event, "Mail server connection failed"));
  }

  @ParameterizedTest(
      name =
          "[MAM-59 {1}] the {0} notice: a failed send stores no SENT notice and announces no"
              + " NoticeSent")
  @MethodSource("noticeTypes")
  void failedSendStoresAndAnnouncesNothing(String code, String ac) {
    mailFailure = new IllegalStateException("down");

    notices.send(NPI, code, UUID.randomUUID());

    assertThat(stored).isEmpty();
    assertThat(sent).isEmpty();
    assertThat(mails).isEmpty();
  }

  @ParameterizedTest(
      name =
          "[MAM-59 {1}] the {0} notice: the template is looked up once, then the failure is"
              + " recorded")
  @MethodSource("noticeTypes")
  void templateIsLookedUpOnce(String code, String ac) {
    mailFailure = new IllegalStateException("down");

    notices.send(NPI, code, UUID.randomUUID());

    assertThat(asked).containsExactly(code);
  }

  // ---- a template that is not set up
  // ----------------------------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-59 {1}] the {0} notice: a template that is not set up is a FAILED notice with the"
              + " cause NOTICE TEMPLATE NOT FOUND {0}, and returns nothing")
  @MethodSource("noticeTypes")
  void missingTemplateIsAFailedNotice(String code, String ac) {
    UUID event = UUID.randomUUID();
    templates.remove(code);

    Optional<Notice> result = notices.send(NPI, code, event);

    assertThat(result).isEmpty();
    assertThat(failed).hasSize(1);
    assertThat(failed.get(0).notice().templateCode()).isEqualTo(code);
    assertThat(failed.get(0).notice().npi()).isEqualTo(NPI);
    assertThat(failed.get(0).notice().eventId()).isEqualTo(event);
    assertThat(failed.get(0).notice().recipient()).isEqualTo("1003000111@providers.pem.local");
    assertThat(failed.get(0).notice().sentAt()).isNull();
    assertThat(failed.get(0).cause()).isEqualTo("NOTICE TEMPLATE NOT FOUND " + code);
    assertThat(asked).containsExactly(code);
  }

  @ParameterizedTest(
      name =
          "[MAM-59 {1}] the {0} notice: a template that is not set up is one WARN line in the words"
              + " of D-024, and nothing is emailed, stored or announced")
  @MethodSource("noticeTypes")
  void missingTemplateWords(String code, String ac) {
    UUID event = UUID.randomUUID();
    templates.remove(code);

    notices.send(NPI, code, event);

    assertThat(log.messages(Level.WARN))
        .containsExactly(warning(code, event, "NOTICE TEMPLATE NOT FOUND " + code));
    assertThat(mails).isEmpty();
    assertThat(stored).isEmpty();
    assertThat(sent).isEmpty();
  }

  // ---- the processing goes on
  // ---------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-59 AC-1] the processing goes on: after a failed send the next notice is sent, stored"
          + " and announced")
  void nextNoticeIsProcessed() {
    mailFailure = new IllegalStateException("down");
    notices.send("1003000111", "APPROVED", UUID.randomUUID());
    mailFailure = null;

    Optional<Notice> second = notices.send("1003000444", "DENIED", UUID.randomUUID());

    assertThat(second).isPresent();
    assertThat(failed).hasSize(1);
    assertThat(mails).extracting(Mail::to).containsExactly("1003000444@providers.pem.local");
    assertThat(stored).containsExactly(second.get());
    assertThat(sent).hasSize(1);
  }

  @ParameterizedTest(
      name =
          "[MAM-59 {1}] the {0} notice: a notice that is sent leaves no FAILED notice, no warning"
              + " and no error")
  @MethodSource("noticeTypes")
  void sentNoticeIsNotFailed(String code, String ac) {
    notices.send(NPI, code, UUID.randomUUID());

    assertThat(stored).hasSize(1);
    assertThat(failed).isEmpty();
    assertThat(log.messages(Level.WARN)).isEmpty();
    assertThat(log.messages(Level.ERROR)).isEmpty();
  }

  // ---- a failure after the email was sent is not a failed send (lesson c)
  // ---------------------------------

  @ParameterizedTest(
      name =
          "[MAM-59 {1}] the {0} notice: a failure of the notice store after the email was sent is"
              + " thrown, so the transaction rolls back; it is not recorded as a FAILED notice"
              + " (lesson c)")
  @MethodSource("noticeTypes")
  void storeFailureIsThrown(String code, String ac) {
    storeFailure = new IllegalStateException("database down");

    assertThatThrownBy(() -> notices.send(NPI, code, UUID.randomUUID())).isSameAs(storeFailure);

    assertThat(failed).isEmpty();
    assertThat(sent).isEmpty();
  }

  @ParameterizedTest(
      name =
          "[MAM-59 {1}] the {0} notice: a failure of the outbox after the email was sent is thrown,"
              + " so the transaction rolls back; it is not recorded as a FAILED notice (lesson c)")
  @MethodSource("noticeTypes")
  void outboxFailureIsThrown(String code, String ac) {
    outboxFailure = new IllegalStateException("outbox down");

    assertThatThrownBy(() -> notices.send(NPI, code, UUID.randomUUID())).isSameAs(outboxFailure);

    assertThat(failed).isEmpty();
  }
}
