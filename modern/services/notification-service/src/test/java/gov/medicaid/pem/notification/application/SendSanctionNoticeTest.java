package gov.medicaid.pem.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MAM-58: a SanctionApplied event gives the TRM, SUS or RST notice; an unknown action is refused; a
 * failed send or a missing template is recorded as FAILED and never thrown (D-012, D-024, D-026).
 */
class SendSanctionNoticeTest {

  static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");

  record Mail(String to, String subject, String body) {}

  record Failed(Notice notice, String cause) {}

  record Sent(String type, String subject, Object data) {}

  final List<Mail> mails = new ArrayList<>();
  final List<Notice> stored = new ArrayList<>();
  final List<Failed> failed = new ArrayList<>();
  final List<Sent> sent = new ArrayList<>();
  final Map<String, Template> templates = new HashMap<>();
  RuntimeException mailFailure;

  {
    templates.put("TRM", new Template("TRM", "Termination notice", "NPI {npi} is terminated."));
    templates.put("SUS", new Template("SUS", "Suspension notice", "NPI {npi} is suspended."));
    templates.put("RST", new Template("RST", "Reinstatement notice", "NPI {npi} is reinstated."));
    templates.put("GEN", new Template("GEN", "General notice", "A notice for NPI {npi}."));
  }

  final SendSanctionNotice notices =
      new SendSanctionNotice(
          code -> Optional.ofNullable(templates.get(code)),
          (to, subject, body) -> {
            if (mailFailure != null) {
              throw mailFailure;
            }
            mails.add(new Mail(to, subject, body));
          },
          stored::add,
          (notice, cause) -> failed.add(new Failed(notice, cause)),
          (type, subject, data) -> sent.add(new Sent(type, subject, data)),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  @DisplayName(
      "[MAM-58 AC-1/AC-5] action T emails the TRM notice, stores it as sent and announces NoticeSent once")
  void terminate() {
    UUID event = UUID.randomUUID();
    assertThat(notices.send("1003000111", "T", event)).isEqualTo(SendSanctionNotice.Outcome.SENT);
    assertThat(mails)
        .containsExactly(
            new Mail(
                "1003000111@providers.pem.local",
                "Termination notice",
                "NPI 1003000111 is terminated."));
    assertThat(stored).hasSize(1);
    Notice n = stored.get(0);
    assertThat(n.npi()).isEqualTo("1003000111");
    assertThat(n.templateCode()).isEqualTo("TRM");
    assertThat(n.recipient()).isEqualTo("1003000111@providers.pem.local");
    assertThat(n.eventId()).isEqualTo(event);
    assertThat(n.sentAt()).isEqualTo(NOW);
    assertThat(sent)
        .containsExactly(
            new Sent(
                "NoticeSent", "1003000111", new NoticeSentData(n.noticeId(), "1003000111", "TRM")));
    assertThat(failed).isEmpty();
  }

  @Test
  @DisplayName("[MAM-58 AC-2] action S emails the SUS notice")
  void suspend() {
    notices.send("1003000111", "S", UUID.randomUUID());
    assertThat(mails)
        .containsExactly(
            new Mail(
                "1003000111@providers.pem.local",
                "Suspension notice",
                "NPI 1003000111 is suspended."));
    assertThat(sent).hasSize(1);
  }

  @Test
  @DisplayName("[MAM-58 AC-3] action R emails the RST notice")
  void reinstate() {
    notices.send("1003000111", "R", UUID.randomUUID());
    assertThat(mails)
        .containsExactly(
            new Mail(
                "1003000111@providers.pem.local",
                "Reinstatement notice",
                "NPI 1003000111 is reinstated."));
    assertThat(sent).hasSize(1);
  }

  @Test
  @DisplayName(
      "[MAM-58 AC-4] an action outside T, S and R is refused: no email (not GEN), nothing stored or announced")
  void refused() {
    for (String action : new String[] {"X", "", null, "GEN"}) {
      assertThat(notices.send("1003000111", action, UUID.randomUUID()))
          .isEqualTo(SendSanctionNotice.Outcome.REFUSED);
    }
    assertThat(mails).isEmpty();
    assertThat(stored).isEmpty();
    assertThat(failed).isEmpty();
    assertThat(sent).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-58 AC-6] a failed send is recorded as FAILED with its cause, not thrown and not announced")
  void mailFails() {
    mailFailure = new IllegalStateException("Mail server connection failed");
    UUID event = UUID.randomUUID();
    assertThat(notices.send("1003000111", "S", event)).isEqualTo(SendSanctionNotice.Outcome.FAILED);
    assertThat(failed).hasSize(1);
    Failed f = failed.get(0);
    assertThat(f.notice().npi()).isEqualTo("1003000111");
    assertThat(f.notice().templateCode()).isEqualTo("SUS");
    assertThat(f.notice().eventId()).isEqualTo(event);
    assertThat(f.notice().sentAt()).isNull();
    assertThat(f.cause()).contains("Mail server connection failed");
    assertThat(stored).isEmpty();
    assertThat(sent).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-58 AC-7] a missing template is a FAILED notice named NOTICE TEMPLATE NOT FOUND, with no email")
  void missingTemplate() {
    templates.remove("TRM");
    assertThat(notices.send("1003000111", "T", UUID.randomUUID()))
        .isEqualTo(SendSanctionNotice.Outcome.FAILED);
    assertThat(failed).hasSize(1);
    assertThat(failed.get(0).notice().templateCode()).isEqualTo("TRM");
    assertThat(failed.get(0).cause()).contains("NOTICE TEMPLATE NOT FOUND TRM");
    assertThat(mails).isEmpty();
    assertThat(stored).isEmpty();
    assertThat(sent).isEmpty();
  }
}
