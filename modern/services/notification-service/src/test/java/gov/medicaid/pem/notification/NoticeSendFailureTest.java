package gov.medicaid.pem.notification;

import static gov.medicaid.pem.notification.NoticeFixtures.mailbox;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.notification.application.SendNotice;
import gov.medicaid.pem.notification.infrastructure.NoticeEventsListener;
import gov.medicaid.pem.testsupport.PemJwt;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Code review of notification-service, 2026-11-20, MUST-FIX 1: the failed-send handling of D-024
 * (MAM-58 AC-6) applies to the APPROVED, DENIED, REJECTED and REVALIDATION_DUE notices of MAM-59 as
 * well, and MAM-60 AC-5 requires that a notice that could not be sent shows as FAILED. The real
 * application, with its real mail sender pointed at a port nobody listens on, or at a port that
 * accepts the connection and never answers (lesson d). The use case and the listener bean are
 * called directly, as {@code SanctionNoticeFailureTest} does, so the mail sender that is changed is
 * the one of this test's own application context (a Kafka event could be consumed by the listener
 * of another cached context; {@code NoticeSendFailureEventsTest} covers the Kafka path with a fault
 * that every context sees). Every test has its own NPIs (10039006xx).
 */
class NoticeSendFailureTest extends IntegrationTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired JavaMailSender mail;
  @Autowired SendNotice notices;
  @Autowired NoticeEventsListener listener;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

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

  // ---- helpers
  // -----------------------------------------------------------------------------------

  /** The notice code, the MAM-59 criterion that sends it, the NPI of its tests, its event. */
  static Stream<Arguments> cases() {
    return Stream.of(
        Arguments.of(
            "APPROVED",
            "AC-1",
            "1003900601",
            "ProviderStatusChanged",
            StatusNoticeEventsTest.statusChanged("1003900601", "P", "A")),
        Arguments.of(
            "DENIED",
            "AC-2",
            "1003900602",
            "ProviderStatusChanged",
            StatusNoticeEventsTest.statusChanged("1003900602", "P", "D")),
        Arguments.of(
            "REJECTED",
            "AC-4",
            "1003900603",
            "ApplicationRejected",
            StatusNoticeEventsTest.rejected("1003900603", "E104", "taxonomy")),
        Arguments.of(
            "REVALIDATION_DUE",
            "AC-6",
            "1003900604",
            "RevalidationDue",
            StatusNoticeEventsTest.revalidationDue("1003900604")));
  }

  private static int closedPort() throws Exception {
    try (ServerSocket s = new ServerSocket(0)) {
      return s.getLocalPort();
    }
  }

  /** Sends the notice with the mail server down; a failed send must not be thrown (D-024). */
  private UUID sendWithMailDown(String npi, String code) throws Exception {
    npis.add(npi);
    UUID event = UUID.randomUUID();
    events.add(event);
    sender.setPort(closedPort());
    assertThatCode(() -> notices.send(npi, code, event))
        .as("a send to a mail server that is down must not be thrown (D-024)")
        .doesNotThrowAnyException();
    return event;
  }

  private String envelope(UUID id, String type, String npi, Map<String, Object> data) {
    events.add(id);
    npis.add(npi);
    return NoticeFixtures.envelope(id, type, npi, data);
  }

  private JsonNode listFor(String npi, RequestPostProcessor token) throws Exception {
    MvcResult r = mvc.perform(get("/api/v1/notices").param("npi", npi).with(token)).andReturn();
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString());
  }

  // ---- the notice use case, called directly
  // ---------------------------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-59 {1}] the {0} notice: a mail server that cannot be reached gives a FAILED notice"
              + " row without sent_at, linked to the event, addressed to the provider's mailbox"
              + " (D-024)")
  @MethodSource("cases")
  void failedRow(String code, String ac, String npi, String type, Map<String, Object> data)
      throws Exception {
    UUID event = sendWithMailDown(npi, code);

    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT template_code, channel, recipient, status, event_id, sent_at FROM notice"
                + " WHERE npi = ?",
            npi);
    assertThat(rows).as("notice rows of %s", npi).hasSize(1);
    assertThat(rows.get(0))
        .containsEntry("template_code", code)
        .containsEntry("channel", "EMAIL")
        .containsEntry("recipient", mailbox(npi))
        .containsEntry("status", "FAILED")
        .containsEntry("event_id", event);
    assertThat(rows.get(0).get("sent_at")).as("sent_at of a notice that was not sent").isNull();
  }

  @ParameterizedTest(
      name =
          "[MAM-59 {1}] the {0} notice: a mail server that cannot be reached is one WARN line"
              + " naming template, NPI, status FAILED, event and a cause (D-024)")
  @MethodSource("cases")
  void warningLine(String code, String ac, String npi, String type, Map<String, Object> data)
      throws Exception {
    UUID event = sendWithMailDown(npi, code);

    String prefix =
        "NOTICE " + code + " FOR NPI " + npi + " STATUS FAILED (event " + event + "), cause: ";
    assertThat(log.messages(Level.WARN))
        .as("WARN lines")
        .anyMatch(m -> m.startsWith(prefix) && !m.substring(prefix.length()).isBlank());
  }

  @ParameterizedTest(
      name =
          "[MAM-59 {1}] the {0} notice: a mail server that cannot be reached sends no email and"
              + " writes no NoticeSent")
  @MethodSource("cases")
  void nothingEmailedAndNothingAnnounced(
      String code, String ac, String npi, String type, Map<String, Object> data) throws Exception {
    sendWithMailDown(npi, code);

    assertThat(NoticeFixtures.mailTo(mailbox(npi))).isEmpty();
    assertThat(NoticeFixtures.noticeSent(jdbc, npi)).isZero();
  }

  @Test
  @DisplayName(
      "[MAM-59 AC-1] a mail server that accepts the connection and never answers gives a FAILED"
          + " APPROVED notice within the send timeout, not a hang (lesson d)")
  void silentMailServer() throws Exception {
    String npi = "1003900605";
    npis.add(npi);
    UUID event = UUID.randomUUID();
    events.add(event);
    try (ServerSocket silent = new ServerSocket(0, 10, InetAddress.getLoopbackAddress())) {
      sender.setHost("127.0.0.1");
      sender.setPort(silent.getLocalPort());
      long started = System.nanoTime();

      assertTimeoutPreemptively(Duration.ofSeconds(40), () -> notices.send(npi, "APPROVED", event));

      assertThat(Duration.ofNanos(System.nanoTime() - started).toSeconds())
          .as("seconds the send waited for the silent mail server")
          .isLessThan(30);
      assertThat(
              NoticeFixtures.count(
                  jdbc,
                  "SELECT count(*) FROM notice WHERE npi = ? AND template_code = 'APPROVED'"
                      + " AND status = 'FAILED'",
                  npi))
          .isEqualTo(1);
      assertThat(NoticeFixtures.noticeSent(jdbc, npi)).isZero();
    }
  }

  // ---- the listener bean, called directly (its transaction is the real one)
  // ----------------------------

  @ParameterizedTest(
      name =
          "[MAM-59 {1}] the {3} event of the {0} notice, with the mail server down, is handled"
              + " without an error: no retry and no dead letter, and its id is in processed_event")
  @MethodSource("cases")
  void listenerAcknowledgesAFailedSend(
      String code, String ac, String npi, String type, Map<String, Object> data) throws Exception {
    UUID id = UUID.randomUUID();
    String message = envelope(id, type, npi, data);
    sender.setPort(closedPort());

    assertThatCode(() -> listener.onEvent(message))
        .as(
            "the listener must not throw when the mail server is down (an error means retries"
                + " and the dead letter topic)")
        .doesNotThrowAnyException();

    assertThat(NoticeFixtures.processedCount(jdbc, id)).isEqualTo(1);
  }

  @ParameterizedTest(
      name =
          "[MAM-59 {1}] the {3} event of the {0} notice, with the mail server down, leaves a FAILED"
              + " notice for the event, committed with the processed marker (D-024)")
  @MethodSource("cases")
  void listenerRecordsTheFailedNotice(
      String code, String ac, String npi, String type, Map<String, Object> data) throws Exception {
    UUID id = UUID.randomUUID();
    String message = envelope(id, type, npi, data);
    sender.setPort(closedPort());

    assertThatCode(() -> listener.onEvent(message)).doesNotThrowAnyException();

    assertThat(
            jdbc.queryForList(
                "SELECT template_code || '/' || status || '/' || event_id FROM notice WHERE npi = ?",
                String.class,
                npi))
        .containsExactly(code + "/FAILED/" + id);
    assertThat(NoticeFixtures.noticeSent(jdbc, npi)).isZero();
  }

  @Test
  @DisplayName(
      "[MAM-59 AC-1] the processing goes on: after an approval whose send failed, the next"
          + " approval of another provider is emailed, stored as SENT and announced")
  void listenerGoesOnAfterAFailure() throws Exception {
    String failedNpi = "1003900611";
    String nextNpi = "1003900612";
    String first =
        envelope(
            UUID.randomUUID(),
            "ProviderStatusChanged",
            failedNpi,
            StatusNoticeEventsTest.statusChanged(failedNpi, "P", "A"));
    String second =
        envelope(
            UUID.randomUUID(),
            "ProviderStatusChanged",
            nextNpi,
            StatusNoticeEventsTest.statusChanged(nextNpi, "P", "A"));

    sender.setPort(closedPort());
    assertThatCode(() -> listener.onEvent(first)).doesNotThrowAnyException();
    sender.setPort(port);
    assertThatCode(() -> listener.onEvent(second)).doesNotThrowAnyException();

    assertThat(NoticeFixtures.subjectsTo(mailbox(nextNpi)))
        .containsExactly(NoticeFixtures.subjectOf(jdbc, "APPROVED"));
    assertThat(jdbc.queryForList("SELECT status FROM notice WHERE npi = ?", String.class, nextNpi))
        .containsExactly("SENT");
    assertThat(NoticeFixtures.noticeSent(jdbc, nextNpi)).isEqualTo(1);
    assertThat(
            jdbc.queryForList("SELECT status FROM notice WHERE npi = ?", String.class, failedNpi))
        .containsExactly("FAILED");
  }

  // ---- MAM-60 AC-5: the notice list shows it
  // --------------------------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-60 AC-5] the {0} notice of a send that failed because the mail server was down is"
              + " listed for an ANALYST with status FAILED, its template and channel EMAIL")
  @MethodSource("cases")
  void failedNoticeIsListedForStaff(
      String code, String ac, String npi, String type, Map<String, Object> data) throws Exception {
    sendWithMailDown(npi, code);

    JsonNode list = listFor(npi, PemJwt.analyst());

    assertThat(list.path("total").asInt()).isEqualTo(1);
    JsonNode n = list.path("items").get(0);
    assertThat(n.path("status").asText()).isEqualTo("FAILED");
    assertThat(n.path("template").asText()).isEqualTo(code);
    assertThat(n.path("channel").asText()).isEqualTo("EMAIL");
    assertThat(n.path("npi").asText()).isEqualTo(npi);
    assertThat(n.has("sentAt") && n.get("sentAt").isNull())
        .as("sentAt of a FAILED notice is %s", n.get("sentAt"))
        .isFalse();
  }

  @ParameterizedTest(
      name =
          "[MAM-60 AC-5] the provider sees the {0} notice that could not be sent as FAILED in their"
              + " own list")
  @MethodSource("cases")
  void failedNoticeIsListedForTheProvider(
      String code, String ac, String npi, String type, Map<String, Object> data) throws Exception {
    sendWithMailDown(npi, code);

    MvcResult r = mvc.perform(get("/api/v1/notices").with(PemJwt.provider(npi))).andReturn();

    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    JsonNode items = json.readTree(r.getResponse().getContentAsString()).path("items");
    List<String> seen = new ArrayList<>();
    items.forEach(n -> seen.add(n.path("template").asText() + "/" + n.path("status").asText()));
    assertThat(seen).containsExactly(code + "/FAILED");
  }
}
