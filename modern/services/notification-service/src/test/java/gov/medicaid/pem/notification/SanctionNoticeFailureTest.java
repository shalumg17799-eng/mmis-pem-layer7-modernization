package gov.medicaid.pem.notification;

import static gov.medicaid.pem.notification.NoticeFixtures.SANCTIONS;
import static gov.medicaid.pem.notification.NoticeFixtures.mailbox;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import ch.qos.logback.classic.Level;
import gov.medicaid.pem.notification.application.SendSanctionNotice;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * MAM-58 Lane 3: a send that fails, with the real mail sender of the application pointed at a port
 * nobody listens on, or at a port that accepts the connection and never answers (lesson d: a server
 * that does not answer ends in a visible failure within the configured timeout, never a hang), and
 * the failure after the email was sent (lesson c: one transaction, so nothing is left half done and
 * the redelivered event is applied once). The use case is called directly, so the mail sender that
 * is changed is the one of this test's own application context. Every test has its own NPIs
 * (10039001xx).
 */
class SanctionNoticeFailureTest extends IntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired JavaMailSender mail;
  @Autowired SendSanctionNotice sanctions;

  private JavaMailSenderImpl sender;
  private String host;
  private int port;
  private LogCapture log;
  private final List<UUID> events = new ArrayList<>();
  private final List<String> npis = new ArrayList<>();

  @BeforeEach
  void start() {
    sender = (JavaMailSenderImpl) mail;
    host = sender.getHost();
    port = sender.getPort();
    log = LogCapture.open();
  }

  @AfterEach
  void stop() {
    sender.setHost(host);
    sender.setPort(port);
    log.close();
    NoticeFixtures.restoreOutbox(jdbc);
    NoticeFixtures.clean(jdbc, events, npis.toArray(String[]::new));
  }

  private int closedPort() throws Exception {
    try (ServerSocket s = new ServerSocket(0)) {
      return s.getLocalPort();
    }
  }

  @Test
  @DisplayName(
      "[MAM-58 AC-6] a mail server that cannot be reached gives a FAILED notice row without"
          + " sent_at, linked to the event, with a WARN line naming template, NPI, event and cause")
  void refusedConnection() throws Exception {
    String npi = "1003900121";
    npis.add(npi);
    UUID event = UUID.randomUUID();
    sender.setPort(closedPort());

    SendSanctionNotice.Outcome outcome = sanctions.send(npi, "S", event);

    assertThat(outcome).isEqualTo(SendSanctionNotice.Outcome.FAILED);
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT template_code, channel, recipient, status, event_id, sent_at FROM notice"
                + " WHERE npi = ?",
            npi);
    assertThat(row)
        .containsEntry("template_code", "SUS")
        .containsEntry("channel", "EMAIL")
        .containsEntry("recipient", mailbox(npi))
        .containsEntry("status", "FAILED")
        .containsEntry("event_id", event);
    assertThat(row.get("sent_at")).as("sent_at of a notice that was not sent").isNull();
    assertThat(NoticeFixtures.mailTo(mailbox(npi))).isEmpty();
    assertThat(NoticeFixtures.noticeSent(jdbc, npi)).isZero();
    String prefix = "NOTICE SUS FOR NPI " + npi + " STATUS FAILED (event " + event + "), cause: ";
    assertThat(log.messages(Level.WARN))
        .anyMatch(m -> m.startsWith(prefix) && !m.substring(prefix.length()).isBlank());
  }

  @Test
  @DisplayName(
      "[MAM-58 AC-6] a mail server that accepts the connection and never answers gives a FAILED"
          + " notice within the send timeout, not a hang (lesson d)")
  void silentMailServer() throws Exception {
    String npi = "1003900122";
    npis.add(npi);
    UUID event = UUID.randomUUID();
    try (ServerSocket silent = new ServerSocket(0, 10, InetAddress.getLoopbackAddress())) {
      sender.setHost("127.0.0.1");
      sender.setPort(silent.getLocalPort());
      long started = System.nanoTime();

      SendSanctionNotice.Outcome outcome =
          assertTimeoutPreemptively(Duration.ofSeconds(40), () -> sanctions.send(npi, "T", event));

      assertThat(outcome).isEqualTo(SendSanctionNotice.Outcome.FAILED);
      assertThat(Duration.ofNanos(System.nanoTime() - started).toSeconds())
          .as("seconds the send waited for the silent mail server")
          .isLessThan(30);
      assertThat(
              NoticeFixtures.count(
                  jdbc,
                  "SELECT count(*) FROM notice WHERE npi = ? AND template_code = 'TRM'"
                      + " AND status = 'FAILED'",
                  npi))
          .isEqualTo(1);
      assertThat(NoticeFixtures.noticeSent(jdbc, npi)).isZero();
    }
  }

  @Test
  @DisplayName(
      "[MAM-58 AC-8] a failure after the email was sent rolls the notice, the NoticeSent and the"
          + " processed marker back together; the redelivered event is then applied once (lesson c)")
  void failureAfterTheEmailLeavesNoPartialWork() {
    String npi = "1003900123";
    npis.add(npi);
    UUID id = UUID.randomUUID();
    events.add(id);
    String envelope =
        NoticeFixtures.envelope(
            id, "SanctionApplied", npi, SanctionNoticeEventsTest.applied(npi, "S"));

    NoticeFixtures.failOutboxFor(jdbc, npi);
    try {
      NoticeFixtures.publish(SANCTIONS, npi, envelope);
      events.add(NoticeFixtures.awaitBehind(jdbc, SANCTIONS, npi));

      assertThat(NoticeFixtures.processedCount(jdbc, id))
          .as("processed marker of the event whose outbox write failed")
          .isZero();
      assertThat(NoticeFixtures.notices(jdbc, npi)).as("notice rows").isZero();
      assertThat(NoticeFixtures.noticeSent(jdbc, npi)).as("NoticeSent rows").isZero();
    } finally {
      NoticeFixtures.restoreOutbox(jdbc);
    }

    NoticeFixtures.publish(SANCTIONS, npi, envelope);
    NoticeFixtures.awaitProcessed(jdbc, id);
    assertThat(NoticeFixtures.notices(jdbc, npi)).isEqualTo(1);
    assertThat(NoticeFixtures.noticeSent(jdbc, npi)).isEqualTo(1);
    assertThat(NoticeFixtures.processedCount(jdbc, id)).isEqualTo(1);

    NoticeFixtures.publish(SANCTIONS, npi, envelope);
    events.add(NoticeFixtures.awaitBehind(jdbc, SANCTIONS, npi));
    assertThat(NoticeFixtures.notices(jdbc, npi))
        .as("notice rows after a second delivery")
        .isEqualTo(1);
    assertThat(NoticeFixtures.noticeSent(jdbc, npi))
        .as("NoticeSent rows after a second delivery")
        .isEqualTo(1);
  }
}
