package gov.medicaid.pem.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import gov.medicaid.pem.notification.LogCapture;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
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

/**
 * MAM-59 Lane 3, unit level (fake ports, fixed Clock 2026-11-20): what SendNotice returns, the
 * words of the line for a template that is not set up, that the body is filled in for each NPI, and
 * that each of the four notice codes of MAM-59 is sent with its own template.
 */
class SendNoticeResultTest {

  static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

  record Mail(String to, String subject, String body) {}

  record Sent(String type, String subject, Object data) {}

  record Failed(Notice notice, String cause) {}

  final List<String> asked = new ArrayList<>();
  final List<Mail> mails = new ArrayList<>();
  final List<Notice> stored = new ArrayList<>();
  final List<Failed> failed = new ArrayList<>();
  final List<Sent> sent = new ArrayList<>();
  final Map<String, Template> templates =
      Map.of(
          "APPROVED", new Template("APPROVED", "Enrolment approved", "NPI {npi} is approved."),
          "DENIED", new Template("DENIED", "Enrolment denied", "NPI {npi} is denied."),
          "REJECTED", new Template("REJECTED", "Application rejected", "NPI {npi} was rejected."),
          "REVALIDATION_DUE", new Template("REVALIDATION_DUE", "Revalidation due", "NPI {npi}."));

  final SendNotice notices =
      new SendNotice(
          code -> {
            asked.add(code);
            return Optional.ofNullable(templates.get(code));
          },
          (to, subject, body) -> mails.add(new Mail(to, subject, body)),
          stored::add,
          (notice, cause) -> failed.add(new Failed(notice, cause)),
          (type, subject, data) -> sent.add(new Sent(type, subject, data)),
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
  @DisplayName("[MAM-59 AC-1] a sent notice is returned: the one that was stored and announced")
  void returnsTheStoredNotice() {
    UUID event = UUID.randomUUID();

    Optional<Notice> result = notices.send("1003000111", "APPROVED", event);

    assertThat(result).isPresent();
    assertThat(stored).containsExactly(result.get());
    assertThat(result.get().eventId()).isEqualTo(event);
    assertThat(sent)
        .containsExactly(
            new Sent(
                "NoticeSent",
                "1003000111",
                new NoticeSentData(result.get().noticeId(), "1003000111", "APPROVED")));
  }

  @Test
  @DisplayName(
      "[MAM-59 AC-6] a template that is not set up returns nothing, is recorded as a FAILED notice"
          + " with the cause NOTICE TEMPLATE NOT FOUND <code> and is one WARN line naming the"
          + " template, the NPI, FAILED, the event and the cause (D-024)")
  void missingTemplateIsReturnedAndLogged() {
    UUID event = UUID.randomUUID();

    Optional<Notice> result = notices.send("1003000111", "EXTRACT_READY", event);

    assertThat(result).isEmpty();
    assertThat(log.messages(Level.WARN))
        .containsExactly(
            "NOTICE EXTRACT_READY FOR NPI 1003000111 STATUS FAILED (event "
                + event
                + "), cause: NOTICE TEMPLATE NOT FOUND EXTRACT_READY");
    assertThat(failed).hasSize(1);
    assertThat(failed.get(0).notice().templateCode()).isEqualTo("EXTRACT_READY");
    assertThat(failed.get(0).notice().npi()).isEqualTo("1003000111");
    assertThat(failed.get(0).notice().eventId()).isEqualTo(event);
    assertThat(failed.get(0).cause()).isEqualTo("NOTICE TEMPLATE NOT FOUND EXTRACT_READY");
    assertThat(mails).isEmpty();
    assertThat(stored).isEmpty();
    assertThat(sent).isEmpty();
  }

  @ParameterizedTest(
      name =
          "[MAM-59 AC-1/AC-2/AC-4/AC-6] the {0} notice is sent with the {0} template: subject {1},"
              + " body filled in with the NPI")
  @CsvSource({
    "APPROVED,Enrolment approved,NPI 1003000222 is approved.",
    "DENIED,Enrolment denied,NPI 1003000222 is denied.",
    "REJECTED,Application rejected,NPI 1003000222 was rejected.",
    "REVALIDATION_DUE,Revalidation due,NPI 1003000222."
  })
  void eachTemplate(String code, String subject, String body) {
    notices.send("1003000222", code, UUID.randomUUID());

    assertThat(asked).containsExactly(code);
    assertThat(mails).containsExactly(new Mail("1003000222@providers.pem.local", subject, body));
    assertThat(stored.get(0).templateCode()).isEqualTo(code);
    assertThat(stored.get(0).sentAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName(
      "[MAM-59 AC-8] two notices for two providers are two emails, two stored notices and two"
          + " NoticeSent events, each for its own NPI")
  void oneNoticePerProvider() {
    notices.send("1003000111", "APPROVED", UUID.randomUUID());
    notices.send("1003000444", "APPROVED", UUID.randomUUID());

    assertThat(mails)
        .extracting(Mail::to, Mail::body)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(
                "1003000111@providers.pem.local", "NPI 1003000111 is approved."),
            org.assertj.core.groups.Tuple.tuple(
                "1003000444@providers.pem.local", "NPI 1003000444 is approved."));
    assertThat(stored).extracting(Notice::npi).containsExactly("1003000111", "1003000444");
    assertThat(sent).extracting(Sent::subject).containsExactly("1003000111", "1003000444");
    assertThat(stored.get(0).noticeId()).isNotEqualTo(stored.get(1).noticeId());
  }
}
