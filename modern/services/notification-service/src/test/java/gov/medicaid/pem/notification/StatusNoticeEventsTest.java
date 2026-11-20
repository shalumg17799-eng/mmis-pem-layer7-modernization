package gov.medicaid.pem.notification;

import static gov.medicaid.pem.notification.NoticeFixtures.ENROLLMENT;
import static gov.medicaid.pem.notification.NoticeFixtures.PROVIDER;
import static gov.medicaid.pem.notification.NoticeFixtures.REVALIDATION;
import static gov.medicaid.pem.notification.NoticeFixtures.SANCTIONS;
import static gov.medicaid.pem.notification.NoticeFixtures.TMSIS;
import static gov.medicaid.pem.notification.NoticeFixtures.mailbox;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * MAM-59 Lane 3: ProviderStatusChanged, ApplicationRejected, RevalidationDue and ExtractProduced
 * events of the real Kafka, handled by the real application with the real Postgres and Mailpit. The
 * stored notice and the NoticeSent event (lesson f), the transitions that give no notice, a
 * rejection without a usable NPI, the ExtractProduced event keyed by its run id (Q-061 B), the
 * address of every notice type (Q-092 A), and one transaction for the notice, the NoticeSent event
 * and the processed marker (lesson c). Every test has its own NPIs (10039002xx to 10039004xx).
 */
class StatusNoticeEventsTest extends IntegrationTest {

  @Autowired JdbcTemplate jdbc;

  private final List<UUID> events = new ArrayList<>();
  private final List<String> npis = new ArrayList<>();
  private LogCapture log;

  @BeforeEach
  void start() {
    log = LogCapture.open();
  }

  @AfterEach
  void stop() {
    log.close();
    NoticeFixtures.restoreOutbox(jdbc);
    NoticeFixtures.clean(jdbc, events, npis.toArray(String[]::new));
  }

  // ---- helpers
  // -----------------------------------------------------------------------------------

  /** Publishes the event, remembers it for the clean-up, and returns its id once it was handled. */
  private UUID handle(String topic, String type, String key, Map<String, Object> data) {
    UUID id = publish(topic, type, key, data);
    NoticeFixtures.awaitProcessed(jdbc, id);
    return id;
  }

  private UUID publish(String topic, String type, String key, Map<String, Object> data) {
    UUID id = UUID.randomUUID();
    events.add(id);
    if (key.matches("[0-9]{10}")) {
      npis.add(key);
    }
    NoticeFixtures.publish(topic, key, NoticeFixtures.envelope(id, type, key, data));
    return id;
  }

  static Map<String, Object> statusChanged(String npi, String from, String to) {
    Map<String, Object> d = new LinkedHashMap<>();
    d.put("npi", npi);
    d.put("from", from);
    d.put("to", to);
    d.put("actor", "analyst1");
    return d;
  }

  static Map<String, Object> rejected(String npi, String code, String field) {
    Map<String, Object> d = new LinkedHashMap<>();
    d.put("applicationId", UUID.randomUUID().toString());
    if (npi != null) {
      d.put("npi", npi);
    }
    d.put("errors", List.of(Map.of("code", code, "field", field)));
    return d;
  }

  static Map<String, Object> revalidationDue(String npi) {
    Map<String, Object> d = new LinkedHashMap<>();
    d.put("caseId", UUID.randomUUID().toString());
    d.put("npi", npi);
    d.put("dueDate", "2026-03-31");
    return d;
  }

  private void assertNoticeSent(String npi, String template) {
    List<JsonNode> sent = NoticeFixtures.noticeSentEvents(jdbc, npi);
    assertThat(sent).hasSize(1);
    assertThat(EventsContractFile.violations("NoticeSentEnvelope", sent.get(0))).isEmpty();
    assertThat(sent.get(0).path("data").path("template").asText()).isEqualTo(template);
    assertThat(sent.get(0).path("data").path("npi").asText()).isEqualTo(npi);
  }

  // ---- AC-1, AC-2: the status changes that give a notice
  // -------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-59 AC-1/AC-2] ProviderStatusChanged P to {0} for NPI {3} is stored as a SENT {1}"
              + " notice with the subject {2}, and its NoticeSent event is valid")
  @CsvSource({"A,APPROVED,Enrolment approved,1003900201", "D,DENIED,Enrolment denied,1003900202"})
  void statusChangeGivesANotice(String to, String template, String subject, String npi) {
    UUID event = handle(PROVIDER, "ProviderStatusChanged", npi, statusChanged(npi, "P", to));

    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT template_code, channel, recipient, status, event_id, sent_at FROM notice"
                + " WHERE npi = ?",
            npi);
    assertThat(row)
        .containsEntry("template_code", template)
        .containsEntry("channel", "EMAIL")
        .containsEntry("recipient", mailbox(npi))
        .containsEntry("status", "SENT")
        .containsEntry("event_id", event);
    assertThat(row.get("sent_at")).isNotNull();
    assertThat(NoticeFixtures.subjectsTo(mailbox(npi))).containsExactly(subject);
    assertNoticeSent(npi, template);
  }

  // ---- AC-3: the transitions that give no notice
  // ---------------------------------------------------

  static Stream<Arguments> silentTransitions() {
    return Stream.of(
        Arguments.of("A", "S", "1003900211"),
        Arguments.of("S", "A", "1003900212"),
        Arguments.of("A", "T", "1003900213"),
        Arguments.of("A", "A", "1003900214"),
        Arguments.of("D", "A", "1003900215"),
        Arguments.of("P", "T", "1003900216"),
        Arguments.of("P", "S", "1003900217"),
        Arguments.of("P", "P", "1003900218"));
  }

  @ParameterizedTest(
      name =
          "[MAM-59 AC-3] ProviderStatusChanged {0} to {1} for NPI {2} sends no email and stores no"
              + " notice, and is acknowledged")
  @MethodSource("silentTransitions")
  void transitionGivesNoNotice(String from, String to, String npi) {
    handle(PROVIDER, "ProviderStatusChanged", npi, statusChanged(npi, from, to));

    assertThat(NoticeFixtures.mailTo(mailbox(npi))).isEmpty();
    assertThat(NoticeFixtures.notices(jdbc, npi)).isZero();
    assertThat(NoticeFixtures.noticeSent(jdbc, npi)).isZero();
  }

  // ---- AC-4, AC-5: a rejected application
  // ----------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-59 AC-4] a rejected application is stored as a SENT REJECTED notice for the NPI and"
          + " its NoticeSent event is valid")
  void rejectionGivesANotice() {
    String npi = "1003900221";
    UUID event = handle(ENROLLMENT, "ApplicationRejected", npi, rejected(npi, "E104", "taxonomy"));

    assertThat(
            jdbc.queryForObject(
                "SELECT template_code || '/' || status || '/' || event_id FROM notice"
                    + " WHERE npi = ?",
                String.class,
                npi))
        .isEqualTo("REJECTED/SENT/" + event);
    assertThat(NoticeFixtures.subjectsTo(mailbox(npi))).containsExactly("Application rejected");
    assertNoticeSent(npi, "REJECTED");
  }

  static Stream<Arguments> unusableNpis() {
    Map<String, Object> nullNpi = rejected(null, "E101", "npi");
    nullNpi.put("npi", null);
    Map<String, Object> shortNpi = rejected("12345", "E101", "npi");
    Map<String, Object> letters = rejected("ABCDEFGHIJ", "E102", "npi");
    return Stream.of(
        Arguments.of("no npi member", rejected(null, "E101", "npi")),
        Arguments.of("a null npi", nullNpi),
        Arguments.of("an npi of five digits", shortNpi),
        Arguments.of("an npi of ten letters", letters));
  }

  @ParameterizedTest(
      name =
          "[MAM-59 AC-5] a rejection with {0} sends no email, stores no notice and writes no"
              + " NoticeSent; an INFO line names the event id; the event is acknowledged")
  @MethodSource("unusableNpis")
  void rejectionWithoutUsableNpi(String label, Map<String, Object> data) {
    int noticesBefore = NoticeFixtures.count(jdbc, "SELECT count(*) FROM notice");
    int sentBefore =
        NoticeFixtures.count(jdbc, "SELECT count(*) FROM outbox_event WHERE type = 'NoticeSent'");
    String key = UUID.randomUUID().toString();

    UUID event = handle(ENROLLMENT, "ApplicationRejected", key, data);

    assertThat(NoticeFixtures.count(jdbc, "SELECT count(*) FROM notice")).isEqualTo(noticesBefore);
    assertThat(
            NoticeFixtures.count(
                jdbc, "SELECT count(*) FROM outbox_event WHERE type = 'NoticeSent'"))
        .isEqualTo(sentBefore);
    assertThat(log.messages(Level.INFO)).anyMatch(m -> m.contains(event.toString()));
    assertThat(log.messagesAtLeast(Level.WARN, event.toString())).isEmpty();
  }

  // ---- AC-6: revalidation due
  // ----------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-59 AC-6] a RevalidationDue event is stored as a SENT REVALIDATION_DUE notice for the"
          + " NPI of the event and its NoticeSent event is valid")
  void revalidationGivesANotice() {
    String npi = "1003900231";
    UUID event = handle(REVALIDATION, "RevalidationDue", npi, revalidationDue(npi));

    assertThat(
            jdbc.queryForObject(
                "SELECT template_code || '/' || status || '/' || event_id FROM notice"
                    + " WHERE npi = ?",
                String.class,
                npi))
        .isEqualTo("REVALIDATION_DUE/SENT/" + event);
    assertThat(NoticeFixtures.subjectsTo(mailbox(npi)))
        .containsExactly(NoticeFixtures.subjectOf(jdbc, "REVALIDATION_DUE"));
    assertNoticeSent(npi, "REVALIDATION_DUE");
  }

  // ---- AC-7: ExtractProduced keyed by the run id (events.yaml 1.1.0, Q-061 B)
  // ------------------------

  @Test
  @DisplayName(
      "[MAM-59 AC-7] an ExtractProduced event keyed by its run id (Q-061 B) is consumed and only"
          + " logged: no email, no notice, no NoticeSent, no warning, and it is acknowledged")
  void extractProducedKeyedByRunId() {
    UUID run = UUID.randomUUID();
    int noticesBefore = NoticeFixtures.count(jdbc, "SELECT count(*) FROM notice");
    int sentBefore =
        NoticeFixtures.count(jdbc, "SELECT count(*) FROM outbox_event WHERE type = 'NoticeSent'");
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("runId", run.toString());
    data.put("businessDate", "2026-03-31");
    data.put("periodYyddd", "26090");
    data.put("rowCount", 3);

    UUID event = handle(TMSIS, "ExtractProduced", run.toString(), data);

    assertThat(NoticeFixtures.mailTo(mailbox(run.toString()))).isEmpty();
    assertThat(NoticeFixtures.count(jdbc, "SELECT count(*) FROM notice")).isEqualTo(noticesBefore);
    assertThat(
            NoticeFixtures.count(
                jdbc, "SELECT count(*) FROM outbox_event WHERE type = 'NoticeSent'"))
        .isEqualTo(sentBefore);
    assertThat(log.messages(Level.INFO))
        .anyMatch(m -> m.contains(event.toString()) && m.contains(run.toString()));
    assertThat(log.messagesAtLeast(Level.WARN, event.toString())).isEmpty();
  }

  // ---- AC-8: a redelivered event
  // --------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-59 AC-8] a redelivered approval leaves one notice row, one NoticeSent, one email and"
          + " one processed marker")
  void redeliveredApprovalIsAppliedOnce() {
    String npi = "1003900241";
    npis.add(npi);
    UUID id = UUID.randomUUID();
    events.add(id);
    String envelope =
        NoticeFixtures.envelope(id, "ProviderStatusChanged", npi, statusChanged(npi, "P", "A"));
    NoticeFixtures.publish(PROVIDER, npi, envelope);
    NoticeFixtures.awaitProcessed(jdbc, id);

    NoticeFixtures.publish(PROVIDER, npi, envelope);
    NoticeFixtures.publish(PROVIDER, npi, envelope);
    events.add(NoticeFixtures.awaitBehind(jdbc, PROVIDER, npi));

    assertThat(NoticeFixtures.notices(jdbc, npi)).isEqualTo(1);
    assertThat(NoticeFixtures.noticeSent(jdbc, npi)).isEqualTo(1);
    assertThat(NoticeFixtures.processedCount(jdbc, id)).isEqualTo(1);
    assertThat(NoticeFixtures.mailTo(mailbox(npi))).hasSize(1);
  }

  @Test
  @DisplayName(
      "[MAM-59 AC-8] a failure after the email was sent rolls the notice, the NoticeSent and the"
          + " processed marker back together; the redelivered event is then applied once"
          + " (lesson c)")
  void failureAfterTheEmailLeavesNoPartialWork() {
    String npi = "1003900242";
    npis.add(npi);
    UUID id = UUID.randomUUID();
    events.add(id);
    String envelope =
        NoticeFixtures.envelope(id, "ProviderStatusChanged", npi, statusChanged(npi, "P", "A"));

    NoticeFixtures.failOutboxFor(jdbc, npi);
    try {
      NoticeFixtures.publish(PROVIDER, npi, envelope);
      events.add(NoticeFixtures.awaitBehind(jdbc, PROVIDER, npi));

      assertThat(NoticeFixtures.processedCount(jdbc, id))
          .as("processed marker of the event whose outbox write failed")
          .isZero();
      assertThat(NoticeFixtures.notices(jdbc, npi)).as("notice rows").isZero();
      assertThat(NoticeFixtures.noticeSent(jdbc, npi)).as("NoticeSent rows").isZero();
    } finally {
      NoticeFixtures.restoreOutbox(jdbc);
    }

    NoticeFixtures.publish(PROVIDER, npi, envelope);
    NoticeFixtures.awaitProcessed(jdbc, id);
    assertThat(NoticeFixtures.notices(jdbc, npi)).isEqualTo(1);
    assertThat(NoticeFixtures.noticeSent(jdbc, npi)).isEqualTo(1);
    assertThat(NoticeFixtures.processedCount(jdbc, id)).isEqualTo(1);
  }

  // ---- Q-092: the address of every notice type
  // --------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-59 AC-1] all seven notice types are addressed to <npi>@providers.pem.local and to no"
          + " other mailbox (Q-092 A)")
  void everyNoticeTypeGoesToTheSameAddress() {
    String npi = "1003900251";
    handle(PROVIDER, "ProviderStatusChanged", npi, statusChanged(npi, "P", "A"));
    handle(PROVIDER, "ProviderStatusChanged", npi, statusChanged(npi, "P", "D"));
    handle(ENROLLMENT, "ApplicationRejected", npi, rejected(npi, "E104", "taxonomy"));
    handle(REVALIDATION, "RevalidationDue", npi, revalidationDue(npi));
    for (String action : List.of("T", "S", "R")) {
      handle(SANCTIONS, "SanctionApplied", npi, SanctionNoticeEventsTest.applied(npi, action));
    }

    List<String> expected =
        Stream.of("APPROVED", "DENIED", "REJECTED", "REVALIDATION_DUE", "TRM", "SUS", "RST")
            .map(code -> NoticeFixtures.subjectOf(jdbc, code))
            .sorted()
            .toList();
    assertThat(NoticeFixtures.subjectsTo(mailbox(npi))).isEqualTo(expected);
    assertThat(NoticeFixtures.mailTo(npi + "@example.test")).isEmpty();
    assertThat(
            jdbc.queryForList(
                "SELECT DISTINCT recipient FROM notice WHERE npi = ?", String.class, npi))
        .containsExactly(mailbox(npi));
  }
}
