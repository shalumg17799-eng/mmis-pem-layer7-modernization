package gov.medicaid.pem.provider.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.provider.IntegrationTest;
import gov.medicaid.pem.provider.LogCapture;
import gov.medicaid.pem.provider.ProviderRows;
import gov.medicaid.pem.provider.TestEvents;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * MAM-40: NoticeSent consumed from pem.notification.events (events.yaml, D-012, ADR-003). The
 * listener is called with the raw message, as the Kafka container does, so that every case is exact
 * and fast; one case goes through the real Kafka.
 */
class NoticeEventsListenerTest extends IntegrationTest {

  static final String NPI = "1003000111";
  static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

  @Autowired NoticeEventsListener listener;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  @BeforeEach
  void seed() {
    ProviderRows.wipe(jdbc);
    ProviderRows.individual(NPI).insert(jdbc);
  }

  private String notice(UUID eventId, String npi, UUID noticeId, String template) {
    return TestEvents.envelope(
        json,
        eventId,
        "NoticeSent",
        "notification-service",
        npi,
        UUID.randomUUID().toString(),
        TestEvents.noticeSent(npi, noticeId, template));
  }

  private int noticeEntries(String npi) {
    Integer n =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_log WHERE npi = ? AND action = 'NOTICE_SENT'",
            Integer.class,
            npi);
    return n == null ? 0 : n;
  }

  private int processed(UUID eventId) {
    Integer n =
        jdbc.queryForObject(
            "SELECT count(*) FROM processed_event WHERE event_id = ?", Integer.class, eventId);
    return n == null ? 0 : n;
  }

  private Map<String, Object> providerRow() {
    return jdbc.queryForMap(
        "SELECT version, updated_at, enroll_status, screen_flag, reval_due_date FROM provider WHERE npi = ?",
        NPI);
  }

  @Test
  @DisplayName(
      "[MAM-40 AC-4] a NoticeSent adds one NOTICE_SENT entry with the notice id and the template, and the provider row is untouched")
  void recordsOnce() {
    UUID event = UUID.randomUUID();
    UUID noticeId = UUID.randomUUID();
    Map<String, Object> before = providerRow();

    listener.onEvent(notice(event, NPI, noticeId, "APPROVED"));

    assertThat(noticeEntries(NPI)).isEqualTo(1);
    Map<String, Object> entry =
        jdbc.queryForMap(
            "SELECT actor, before_json::text AS b, after_json::text AS a FROM audit_log WHERE npi = ?",
            NPI);
    assertThat(entry.get("actor")).isEqualTo("notification-service");
    assertThat(entry.get("b")).isNull();
    assertThat((String) entry.get("a")).contains(noticeId.toString()).contains("APPROVED");
    assertThat(providerRow()).isEqualTo(before);
  }

  @Test
  @DisplayName(
      "[MAM-40 AC-4] the same event id delivered a second time adds nothing: one entry, the provider version unchanged")
  void redeliveryIsIgnored() {
    UUID event = UUID.randomUUID();
    String message = notice(event, NPI, UUID.randomUUID(), "APPROVED");
    Map<String, Object> before = providerRow();

    listener.onEvent(message);
    listener.onEvent(message);

    assertThat(noticeEntries(NPI)).isEqualTo(1);
    assertThat(processed(event)).isEqualTo(1);
    assertThat(providerRow()).isEqualTo(before);
  }

  @Test
  @DisplayName(
      "[MAM-40 AC-4] two different notices for the same provider and template are two entries")
  void differentNoticesAreDifferentEntries() {
    listener.onEvent(notice(UUID.randomUUID(), NPI, UUID.randomUUID(), "APPROVED"));
    listener.onEvent(notice(UUID.randomUUID(), NPI, UUID.randomUUID(), "APPROVED"));

    assertThat(noticeEntries(NPI)).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "[MAM-40 AC-4] a NoticeSent for an NPI that is not on file adds no entry and creates no provider")
  void unknownProvider() {
    UUID event = UUID.randomUUID();

    listener.onEvent(notice(event, "1003000999", UUID.randomUUID(), "APPROVED"));

    assertThat(noticeEntries("1003000999")).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM provider WHERE npi = '1003000999'", Integer.class))
        .isZero();
    assertThat(processed(event)).isEqualTo(1);
  }

  @Test
  @DisplayName("[MAM-40 AC-4] an event of another type on the notice topic adds nothing")
  void otherTypesAreIgnored() {
    UUID event = UUID.randomUUID();
    String marker =
        TestEvents.envelope(
            json,
            event,
            "TestMarker",
            "notification-service",
            NPI,
            UUID.randomUUID().toString(),
            Map.of("npi", NPI));

    listener.onEvent(marker);

    assertThat(noticeEntries(NPI)).isZero();
    assertThat(processed(event)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-40 AC-3] the actor is the source named in the envelope, and notification-service when it names none")
  void actorIsTheSource() {
    String viaOther =
        TestEvents.envelope(
            json,
            UUID.randomUUID(),
            "NoticeSent",
            "notification-service-2",
            NPI,
            UUID.randomUUID().toString(),
            TestEvents.noticeSent(NPI, UUID.randomUUID(), "SUSPENDED"));
    String noSource =
        TestEvents.envelope(
            json,
            UUID.randomUUID(),
            "NoticeSent",
            null,
            NPI,
            UUID.randomUUID().toString(),
            TestEvents.noticeSent(NPI, UUID.randomUUID(), "DENIED"));

    listener.onEvent(viaOther);
    listener.onEvent(noSource);

    assertThat(
            jdbc.queryForList(
                "SELECT actor FROM audit_log WHERE npi = ? ORDER BY id", String.class, NPI))
        .containsExactly("notification-service-2", "notification-service");
  }

  @Test
  @DisplayName(
      "[MAM-40 AC-4] the processed marker and the history entry are one transaction: when the entry cannot be written the marker is rolled back too, so a re-run applies the notice")
  void markerAndEntryShareTheTransaction() {
    UUID event = UUID.randomUUID();
    String message = notice(event, NPI, UUID.randomUUID(), "APPROVED");
    jdbc.execute(
        "CREATE FUNCTION fail_notice_insert() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN"
            + " IF NEW.action = 'NOTICE_SENT' THEN RAISE EXCEPTION 'injected failure after the marker';"
            + " END IF; RETURN NEW; END $$");
    jdbc.execute(
        "CREATE TRIGGER fail_notice_insert BEFORE INSERT ON audit_log FOR EACH ROW"
            + " EXECUTE FUNCTION fail_notice_insert()");
    try {
      assertThatThrownBy(() -> listener.onEvent(message)).isInstanceOf(RuntimeException.class);

      assertThat(noticeEntries(NPI)).as("entries after the failed run").isZero();
      assertThat(processed(event)).as("processed marker after the failed run").isZero();
    } finally {
      jdbc.execute("DROP TRIGGER IF EXISTS fail_notice_insert ON audit_log");
      jdbc.execute("DROP FUNCTION IF EXISTS fail_notice_insert()");
    }

    listener.onEvent(message);

    assertThat(noticeEntries(NPI)).as("entries after the re-run").isEqualTo(1);
    assertThat(processed(event)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-40 AC-2] a correlation id with a line break or a forged log line taken from an event never reaches the log or the history unsanitised")
  void unsafeCorrelationIdFromTheEvent() {
    UUID event = UUID.randomUUID();
    String forged = "abc\nERROR forged line from the sender";
    try (LogCapture log = LogCapture.start()) {
      TestEvents.sendAndAwait(
          json,
          jdbc,
          TestEvents.NOTIFICATION,
          event,
          "NoticeSent",
          "notification-service",
          NPI,
          forged,
          TestEvents.noticeSent(NPI, UUID.randomUUID(), "APPROVED"));

      List<LogCapture.Line> lines = log.lines();
      assertThat(lines)
          .allSatisfy(
              l -> {
                assertThat(l.message()).doesNotContain("forged");
                if (l.correlationId() != null) {
                  assertThat(l.correlationId()).matches(SAFE);
                }
              });
    }

    Map<String, Object> entry =
        jdbc.queryForMap(
            "SELECT correlation_id::text AS c FROM audit_log WHERE npi = ? AND action = 'NOTICE_SENT'",
            NPI);
    assertThat(entry.get("c")).as("a generated id, as a uuid").isNotNull();
    assertThat(UUID.fromString((String) entry.get("c"))).isNotNull();
  }
}
