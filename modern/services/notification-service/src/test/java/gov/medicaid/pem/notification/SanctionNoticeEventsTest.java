package gov.medicaid.pem.notification;

import static gov.medicaid.pem.notification.NoticeFixtures.SANCTIONS;
import static gov.medicaid.pem.notification.NoticeFixtures.mailbox;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
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
 * MAM-58 Lane 3: the SanctionApplied events of the real Kafka, handled by the real application,
 * with the real Postgres and Mailpit. What the stored notice looks like, that the NoticeSent event
 * is the contract's NoticeSentEnvelope (lesson f), that a correlation id taken from the event never
 * reaches a log line or the outgoing event unsanitised (lesson g), and the refusal of actions
 * outside T, S and R. Every test has its own NPIs (10039001xx).
 */
class SanctionNoticeEventsTest extends IntegrationTest {

  /** A correlation id that tries to forge a log line. */
  static final String HOSTILE = "abc\r\n2026-11-20 ERROR forged line";

  static final String SAFE = "[A-Za-z0-9._:-]{1,64}";

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
    NoticeFixtures.clean(jdbc, events, npis.toArray(String[]::new));
  }

  static Map<String, Object> applied(String npi, String action) {
    Map<String, Object> d = new LinkedHashMap<>();
    d.put("sanctionId", UUID.randomUUID().toString());
    d.put("npi", npi);
    if (action != null) {
      d.put("action", action);
    }
    d.put("reason", "LEIE exclusion");
    d.put("effectiveDate", "2026-03-31");
    return d;
  }

  /** Publishes SanctionApplied for the NPI and returns its event id once the service handled it. */
  private UUID apply(String npi, String action, String correlationId) {
    npis.add(npi);
    UUID id = UUID.randomUUID();
    events.add(id);
    NoticeFixtures.publish(
        SANCTIONS,
        npi,
        NoticeFixtures.envelope(id, "SanctionApplied", npi, correlationId, applied(npi, action)));
    NoticeFixtures.awaitProcessed(jdbc, id);
    return id;
  }

  @ParameterizedTest(
      name =
          "[MAM-58 AC-1/AC-2/AC-3] action {0} for NPI {2} is stored as a SENT EMAIL notice {1} for"
              + " the provider's mailbox, linked to the event")
  @CsvSource({"T,TRM,1003900101", "S,SUS,1003900102", "R,RST,1003900103"})
  void storedNotice(String action, String template, String npi) {
    UUID event = apply(npi, action, UUID.randomUUID().toString());

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
    assertThat(NoticeFixtures.subjectsTo(mailbox(npi)))
        .containsExactly(NoticeFixtures.subjectOf(jdbc, template));
  }

  @Test
  @DisplayName(
      "[MAM-58 AC-5] the NoticeSent event of a sent notice is a valid NoticeSentEnvelope of"
          + " events.yaml and carries the stored notice id (lesson f)")
  void noticeSentConformsToTheContract() {
    String npi = "1003900104";
    apply(npi, "T", UUID.randomUUID().toString());

    List<JsonNode> sent = NoticeFixtures.noticeSentEvents(jdbc, npi);
    assertThat(sent).hasSize(1);
    JsonNode event = sent.get(0);
    assertThat(EventsContractFile.violations("NoticeSentEnvelope", event)).isEmpty();
    UUID noticeId =
        jdbc.queryForObject("SELECT public_id FROM notice WHERE npi = ?", UUID.class, npi);
    assertThat(event.path("data").path("noticeId").asText()).isEqualTo(noticeId.toString());
    assertThat(event.path("data").path("template").asText()).isEqualTo("TRM");
    assertThat(event.path("subject").asText()).isEqualTo(npi);
  }

  @Test
  @DisplayName(
      "[MAM-58 AC-5] a correlation id of the incoming event that could forge a log line is"
          + " replaced in the NoticeSent event (lesson g)")
  void hostileCorrelationIdIsNotForwardedInNoticeSent() {
    String npi = "1003900105";
    apply(npi, "S", HOSTILE);

    JsonNode event = NoticeFixtures.noticeSentEvents(jdbc, npi).get(0);
    assertThat(event.path("correlationId").asText()).isNotEqualTo(HOSTILE).matches(SAFE);
    assertThat(EventsContractFile.violations("NoticeSentEnvelope", event)).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-58 AC-4] a correlation id of the incoming event that could forge a log line is"
          + " replaced in the log lines written while the event is handled (lesson g)")
  void hostileCorrelationIdIsNotWrittenToTheLog() {
    UUID id = apply("1003900106", "X", HOSTILE); // refused: the service writes an ERROR line

    List<ILoggingEvent> lines =
        log.events().stream().filter(e -> e.getFormattedMessage().contains(id.toString())).toList();
    assertThat(lines).isNotEmpty();
    for (ILoggingEvent line : lines) {
      assertThat(LogCapture.mdc(line).get("correlationId"))
          .isNotNull()
          .isNotEqualTo(HOSTILE)
          .matches(SAFE);
    }
    assertThat(log.events())
        .noneMatch(
            e ->
                e.getFormattedMessage().contains("forged line")
                    || LogCapture.mdc(e).values().stream().anyMatch(v -> v.contains("forged")));
  }

  static Stream<Arguments> badActions() {
    return Stream.of(
        Arguments.of("no action at all", null, "1003900111"),
        Arguments.of("the lower-case s", "s", "1003900112"),
        Arguments.of("the template code GEN", "GEN", "1003900113"),
        Arguments.of("an empty action", "", "1003900114"),
        Arguments.of("two letters TS", "TS", "1003900115"));
  }

  @ParameterizedTest(
      name =
          "[MAM-58 AC-4] {0}: no email, no notice, no NoticeSent; an ERROR line names the event"
              + " id and the action; the event is acknowledged")
  @MethodSource("badActions")
  void refusedAction(String label, String action, String npi) {
    UUID id = apply(npi, action, UUID.randomUUID().toString());

    assertThat(NoticeFixtures.mailTo(mailbox(npi))).isEmpty();
    assertThat(NoticeFixtures.notices(jdbc, npi)).isZero();
    assertThat(NoticeFixtures.noticeSent(jdbc, npi)).isZero();
    assertThat(log.messages(Level.ERROR))
        .anyMatch(m -> m.contains(id.toString()) && m.contains("unknown action " + action));
  }

  @Test
  @DisplayName(
      "[MAM-58 AC-8] a redelivered event leaves one notice row, one NoticeSent, one email and one"
          + " processed marker")
  void redeliveredEventIsAppliedOnce() {
    String npi = "1003900107";
    npis.add(npi);
    UUID id = UUID.randomUUID();
    events.add(id);
    String envelope = NoticeFixtures.envelope(id, "SanctionApplied", npi, applied(npi, "S"));
    NoticeFixtures.publish(SANCTIONS, npi, envelope);
    NoticeFixtures.awaitProcessed(jdbc, id);

    NoticeFixtures.publish(SANCTIONS, npi, envelope);
    NoticeFixtures.publish(SANCTIONS, npi, envelope);
    events.add(NoticeFixtures.awaitBehind(jdbc, SANCTIONS, npi));

    assertThat(NoticeFixtures.notices(jdbc, npi)).isEqualTo(1);
    assertThat(NoticeFixtures.noticeSent(jdbc, npi)).isEqualTo(1);
    assertThat(NoticeFixtures.processedCount(jdbc, id)).isEqualTo(1);
    assertThat(NoticeFixtures.mailTo(mailbox(npi))).hasSize(1);
  }
}
