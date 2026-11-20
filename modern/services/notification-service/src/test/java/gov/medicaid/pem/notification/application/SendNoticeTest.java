package gov.medicaid.pem.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MAM-59: one notice per event: the email is sent, the notice is stored and NoticeSent is announced
 * once.
 */
class SendNoticeTest {

  record Mail(String to, String subject, String body) {}

  record Sent(String type, String subject, Object data) {}

  record Failed(Notice notice, String cause) {}

  final List<Mail> mails = new ArrayList<>();
  final List<Notice> stored = new ArrayList<>();
  final List<Failed> failed = new ArrayList<>();
  final List<Sent> sent = new ArrayList<>();
  final Map<String, Template> templates =
      Map.of(
          "APPROVED", new Template("APPROVED", "Enrolment approved", "NPI {npi} is approved."),
          "DENIED", new Template("DENIED", "Enrolment denied", "NPI {npi} is denied."));

  final SendNotice notices =
      new SendNotice(
          code -> Optional.ofNullable(templates.get(code)),
          (to, subject, body) -> mails.add(new Mail(to, subject, body)),
          stored::add,
          (notice, cause) -> failed.add(new Failed(notice, cause)),
          (type, subject, data) -> sent.add(new Sent(type, subject, data)),
          Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC));

  @Test
  @DisplayName(
      "[MAM-59 AC-1] an approval emails the APPROVED template to the provider, stores and announces it")
  void approved() {
    UUID event = UUID.randomUUID();
    notices.send("1003000111", "APPROVED", event);
    assertThat(mails)
        .containsExactly(
            new Mail(
                "1003000111@providers.pem.local",
                "Enrolment approved",
                "NPI 1003000111 is approved."));
    assertThat(stored).hasSize(1);
    Notice n = stored.get(0);
    assertThat(n.npi()).isEqualTo("1003000111");
    assertThat(n.templateCode()).isEqualTo("APPROVED");
    assertThat(n.recipient()).isEqualTo("1003000111@providers.pem.local");
    assertThat(n.eventId()).isEqualTo(event);
    assertThat(n.sentAt()).isEqualTo(Instant.parse("2026-11-18T10:00:00Z"));
    assertThat(sent)
        .containsExactly(
            new Sent(
                "NoticeSent",
                "1003000111",
                new NoticeSentData(n.noticeId(), "1003000111", "APPROVED")));
  }

  @Test
  @DisplayName(
      "[MAM-59] a template that is not set up sends nothing and announces nothing, and is recorded"
          + " as a FAILED notice with the cause NOTICE TEMPLATE NOT FOUND REVALIDATION_DUE (D-024)")
  void missingTemplate() {
    UUID event = UUID.randomUUID();
    notices.send("1003000111", "REVALIDATION_DUE", event);
    assertThat(mails).isEmpty();
    assertThat(stored).isEmpty();
    assertThat(sent).isEmpty();
    assertThat(failed).hasSize(1);
    assertThat(failed.get(0).notice().templateCode()).isEqualTo("REVALIDATION_DUE");
    assertThat(failed.get(0).notice().eventId()).isEqualTo(event);
    assertThat(failed.get(0).cause()).isEqualTo("NOTICE TEMPLATE NOT FOUND REVALIDATION_DUE");
  }
}
