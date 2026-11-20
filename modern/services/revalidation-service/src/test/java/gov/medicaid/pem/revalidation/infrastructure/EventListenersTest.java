package gov.medicaid.pem.revalidation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.medicaid.pem.revalidation.IntegrationTest;
import gov.medicaid.pem.revalidation.RevalidationFixtures;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The three consumers of revalidation-service called as Kafka calls them, with the message text
 * (real Postgres): ProviderStatusChanged feeds the read model and cancels cases (MAM-45, MAM-49),
 * ScreeningCompleted closes cases (MAM-48, MAM-49), and the service's own RevalidationDue moves a
 * case to IN_SCREENING (MAM-49). Every event id is applied once, and the marker that an event was
 * applied is written in the same transaction as the change (lesson c of the earlier reviews): a
 * failed change leaves no marker, so the event is applied when it comes again, and exactly once.
 */
@Timeout(180)
class EventListenersTest extends IntegrationTest {

  static final LocalDate DUE = LocalDate.of(2026, 2, 28);

  @Autowired ProviderEventsListener providerEvents;
  @Autowired ScreeningEventsListener screeningEvents;
  @Autowired RevalidationEventsListener revalidationEvents;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void cleanBefore() {
    RevalidationFixtures.reset(jdbc);
  }

  @AfterEach
  void cleanAfter() {
    RevalidationFixtures.reset(jdbc);
  }

  private int processed(UUID eventId) {
    return RevalidationFixtures.count(
        jdbc, "SELECT count(*) FROM processed_event WHERE event_id = ?", eventId);
  }

  private String tracked(String npi) {
    List<String> rows =
        jdbc.queryForList(
            "SELECT enroll_status || '|' || coalesce(reval_due_date::text, 'none')"
                + " FROM tracked_provider WHERE npi = ?",
            String.class,
            npi);
    return rows.isEmpty() ? "untracked" : rows.get(0);
  }

  // ---- ProviderStatusChanged (MAM-45, MAM-49) --------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-45 AC-3] a ProviderStatusChanged sets the tracked status and due date of the provider")
  void providerStatusChangedIsTracked() {
    providerEvents.onEvent(
        RevalidationFixtures.providerStatusChanged(
            UUID.randomUUID(), "1003000222", "P", "A", "2026-02-28"));

    assertThat(tracked("1003000222")).isEqualTo("A|2026-02-28");
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-7] a later ProviderStatusChanged from A to A moves the tracked due date forward")
  void laterEventMovesTheDate() {
    providerEvents.onEvent(
        RevalidationFixtures.providerStatusChanged(
            UUID.randomUUID(), "1003000222", "P", "A", "2026-02-28"));
    providerEvents.onEvent(
        RevalidationFixtures.providerStatusChanged(
            UUID.randomUUID(), "1003000222", "A", "A", "2031-03-31"));

    assertThat(tracked("1003000222")).isEqualTo("A|2031-03-31");
  }

  @Test
  @DisplayName("[MAM-45 AC-6] a null or a missing revalidationDueDate is tracked as no due date")
  void noDueDateInTheEvent() {
    providerEvents.onEvent(
        RevalidationFixtures.providerStatusChanged(
            UUID.randomUUID(), "1003000222", "P", "A", null));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("npi", "1003000333");
    data.put("from", "P");
    data.put("to", "A");
    providerEvents.onEvent(
        RevalidationFixtures.envelope(
            UUID.randomUUID(),
            "ProviderStatusChanged",
            "provider-service",
            "1003000333",
            data,
            UUID.randomUUID().toString()));

    assertThat(tracked("1003000222")).isEqualTo("A|none");
    assertThat(tracked("1003000333")).isEqualTo("A|none");
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-3] the optional fields of the contract (reason, actor, displayName, ...) do not matter")
  void contractOptionalFieldsAreAccepted() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("npi", "1003000222");
    data.put("from", "A");
    data.put("to", "S");
    data.put("reason", "SYNTHETIC REASON");
    data.put("actor", "supervisor1");
    data.put("revalidationDueDate", "2026-02-28");
    data.put("providerType", "IN");
    data.put("displayName", "TESTPROVIDER, SYNTHETIC");
    data.put("taxonomy", "207Q00000X");
    data.put("medicaidId", "123456789");

    providerEvents.onEvent(
        RevalidationFixtures.envelope(
            UUID.randomUUID(),
            "ProviderStatusChanged",
            "provider-service",
            "1003000222",
            data,
            UUID.randomUUID().toString()));

    assertThat(tracked("1003000222")).isEqualTo("S|2026-02-28");
  }

  @Test
  @DisplayName("[MAM-45 AC-3] an event id is applied once: the same event again changes nothing")
  void providerEventIsIdempotent() {
    UUID id = UUID.randomUUID();
    providerEvents.onEvent(
        RevalidationFixtures.providerStatusChanged(id, "1003000222", "P", "A", "2026-02-28"));

    providerEvents.onEvent(
        RevalidationFixtures.providerStatusChanged(id, "1003000222", "A", "S", "2031-03-31"));

    assertThat(tracked("1003000222")).isEqualTo("A|2026-02-28");
    assertThat(processed(id)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-3] other event types on the provider topic are recorded as seen and change nothing")
  void otherProviderEventsAreIgnored() {
    UUID id = UUID.randomUUID();
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("npi", "1003000222");
    providerEvents.onEvent(
        RevalidationFixtures.envelope(
            id,
            "ProviderRegistered",
            "provider-service",
            "1003000222",
            data,
            UUID.randomUUID().toString()));

    assertThat(tracked("1003000222")).isEqualTo("untracked");
    assertThat(processed(id)).isEqualTo(1);
  }

  @ParameterizedTest(name = "[MAM-45 AC-3] the message {0} is refused and leaves no trace")
  @ValueSource(strings = {"", "not json", "[]", "{\"id\":\"not-a-uuid\"}"})
  void unreadableMessagesAreRefused(String message) {
    int before = RevalidationFixtures.count(jdbc, "SELECT count(*) FROM processed_event");

    assertThatThrownBy(() -> providerEvents.onEvent(message)).isInstanceOf(RuntimeException.class);

    assertThat(RevalidationFixtures.count(jdbc, "SELECT count(*) FROM processed_event"))
        .isEqualTo(before);
  }

  @Test
  @DisplayName(
      "[MAM-49 AC-2] a ProviderStatusChanged to S cancels the open case in the same step that tracks the status")
  void leavingActiveCancelsTheCase() {
    UUID caseId = RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "IN_SCREENING");

    providerEvents.onEvent(
        RevalidationFixtures.providerStatusChanged(
            UUID.randomUUID(), "1003000222", "A", "S", "2026-02-28"));

    assertThat(RevalidationFixtures.caseStatus(jdbc, caseId)).isEqualTo("CANCELLED");
    assertThat(tracked("1003000222")).isEqualTo("S|2026-02-28");
    assertThat(
            jdbc.queryForObject(
                "SELECT closed_at IS NOT NULL FROM revalidation_case WHERE public_id = ?",
                Boolean.class,
                caseId))
        .isTrue();
  }

  // ---- lesson c: the marker and the change are one transaction ---------------------------------

  @Test
  @DisplayName(
      "[MAM-45 AC-3] a failed change leaves no processed marker; the same event applied later takes effect once")
  void failedProviderEventIsAppliedWhenItComesAgain() {
    UUID id = UUID.randomUUID();
    String message =
        RevalidationFixtures.providerStatusChanged(id, "1003000222", "P", "A", "2026-02-28");
    RevalidationFixtures.reject(
        jdbc, "tracked", "INSERT", "tracked_provider", "true", "SYNTHETIC DISK FULL");

    assertThatThrownBy(() -> providerEvents.onEvent(message)).isInstanceOf(RuntimeException.class);

    assertThat(processed(id)).as("no marker of an event that was not applied").isZero();
    assertThat(tracked("1003000222")).isEqualTo("untracked");

    RevalidationFixtures.dropFailures(jdbc);
    providerEvents.onEvent(message);
    providerEvents.onEvent(message);

    assertThat(tracked("1003000222")).isEqualTo("A|2026-02-28");
    assertThat(processed(id)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-49 AC-2] a case that cannot be cancelled leaves no processed marker and the tracked status is not changed either")
  void failedCancelRollsEverythingBack() {
    UUID id = UUID.randomUUID();
    UUID caseId = RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "DUE");
    RevalidationFixtures.track(jdbc, "1003000222", "A", DUE);
    String message =
        RevalidationFixtures.providerStatusChanged(id, "1003000222", "A", "S", "2026-02-28");
    RevalidationFixtures.reject(
        jdbc, "cancel", "UPDATE", "revalidation_case", "true", "SYNTHETIC DISK FULL");

    assertThatThrownBy(() -> providerEvents.onEvent(message)).isInstanceOf(RuntimeException.class);

    assertThat(processed(id)).isZero();
    assertThat(tracked("1003000222"))
        .as("the read model is rolled back too")
        .isEqualTo("A|2026-02-28");
    assertThat(RevalidationFixtures.caseStatus(jdbc, caseId)).isEqualTo("DUE");

    RevalidationFixtures.dropFailures(jdbc);
    providerEvents.onEvent(message);

    assertThat(RevalidationFixtures.caseStatus(jdbc, caseId)).isEqualTo("CANCELLED");
    assertThat(tracked("1003000222")).isEqualTo("S|2026-02-28");
    assertThat(processed(id)).isEqualTo(1);
  }

  // ---- ScreeningCompleted (MAM-48 AC-4 to AC-6, MAM-49 AC-3) -----------------------------------

  @ParameterizedTest(
      name =
          "[MAM-48 AC-4] a REVALIDATION screening with result {0} completes the open case and keeps the screening id")
  @ValueSource(strings = {"PASS", "FAIL"})
  void revalidationScreeningCompletesTheCase(String result) {
    UUID caseId = RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "IN_SCREENING");
    UUID screening = UUID.randomUUID();

    screeningEvents.onEvent(
        RevalidationFixtures.screeningCompleted(
            UUID.randomUUID(), "1003000222", "REVALIDATION", result, screening));

    assertThat(RevalidationFixtures.caseStatus(jdbc, caseId)).isEqualTo("COMPLETED");
    assertThat(
            jdbc.queryForObject(
                "SELECT screening_id FROM revalidation_case WHERE public_id = ?",
                UUID.class,
                caseId))
        .isEqualTo(screening);
    assertThat(
            jdbc.queryForObject(
                "SELECT closed_at IS NOT NULL FROM revalidation_case WHERE public_id = ?",
                Boolean.class,
                caseId))
        .isTrue();
  }

  @ParameterizedTest(name = "[MAM-48 AC-6] a {0} screening leaves the open case as it is")
  @ValueSource(strings = {"INITIAL", "MANUAL"})
  void otherReasonsLeaveTheCase(String reason) {
    UUID caseId = RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "DUE");
    UUID id = UUID.randomUUID();

    screeningEvents.onEvent(
        RevalidationFixtures.screeningCompleted(
            id, "1003000222", reason, "PASS", UUID.randomUUID()));

    assertThat(RevalidationFixtures.caseStatus(jdbc, caseId)).isEqualTo("DUE");
    assertThat(processed(id)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-6] a REVALIDATION screening of a provider without an open case is ignored without error and opens no case")
  void noOpenCaseIsIgnored() {
    UUID id = UUID.randomUUID();

    screeningEvents.onEvent(
        RevalidationFixtures.screeningCompleted(
            id, "1003000999", "REVALIDATION", "PASS", UUID.randomUUID()));

    assertThat(RevalidationFixtures.casesOf(jdbc, "1003000999")).isZero();
    assertThat(processed(id)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-49 AC-3] a late REVALIDATION result does not reopen a cancelled case and raises no error")
  void lateResultDoesNotReopen() {
    UUID caseId = RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "CANCELLED");

    screeningEvents.onEvent(
        RevalidationFixtures.screeningCompleted(
            UUID.randomUUID(), "1003000222", "REVALIDATION", "PASS", UUID.randomUUID()));

    assertThat(RevalidationFixtures.caseStatus(jdbc, caseId)).isEqualTo("CANCELLED");
    assertThat(RevalidationFixtures.casesOf(jdbc, "1003000222")).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-4] an event id is applied once: a redelivery does not close the next open case")
  void screeningEventIsIdempotent() {
    UUID id = UUID.randomUUID();
    String message =
        RevalidationFixtures.screeningCompleted(
            id, "1003000222", "REVALIDATION", "PASS", UUID.randomUUID());
    RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "DUE");
    screeningEvents.onEvent(message);
    UUID next = RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "DUE");

    screeningEvents.onEvent(message);

    assertThat(RevalidationFixtures.caseStatus(jdbc, next)).isEqualTo("DUE");
    assertThat(processed(id)).isEqualTo(1);
  }

  @Test
  @DisplayName("[MAM-48 AC-4] other event types on the screening topic change nothing")
  void otherScreeningEventsAreIgnored() {
    UUID caseId = RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "DUE");
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("npi", "1003000222");

    screeningEvents.onEvent(
        RevalidationFixtures.envelope(
            UUID.randomUUID(),
            "ScreeningStarted",
            "screening-service",
            "1003000222",
            data,
            UUID.randomUUID().toString()));

    assertThat(RevalidationFixtures.caseStatus(jdbc, caseId)).isEqualTo("DUE");
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-4] a case that cannot be closed leaves no processed marker; the same event applied later closes it once")
  void failedCloseIsAppliedWhenItComesAgain() {
    UUID id = UUID.randomUUID();
    UUID caseId = RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "DUE");
    String message =
        RevalidationFixtures.screeningCompleted(
            id, "1003000222", "REVALIDATION", "PASS", UUID.randomUUID());
    RevalidationFixtures.reject(
        jdbc, "close", "UPDATE", "revalidation_case", "true", "SYNTHETIC DISK FULL");

    assertThatThrownBy(() -> screeningEvents.onEvent(message)).isInstanceOf(RuntimeException.class);

    assertThat(processed(id)).isZero();
    assertThat(RevalidationFixtures.caseStatus(jdbc, caseId)).isEqualTo("DUE");

    RevalidationFixtures.dropFailures(jdbc);
    screeningEvents.onEvent(message);
    screeningEvents.onEvent(message);

    assertThat(RevalidationFixtures.caseStatus(jdbc, caseId)).isEqualTo("COMPLETED");
    assertThat(processed(id)).isEqualTo(1);
  }

  // ---- RevalidationDue read by the service itself (MAM-49 AC-1) --------------------------------

  @Test
  @DisplayName("[MAM-49 AC-1] the RevalidationDue of a DUE case moves it to IN_SCREENING")
  void revalidationDueMovesTheCase() {
    UUID caseId = RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "DUE");

    revalidationEvents.onEvent(
        RevalidationFixtures.revalidationDue(
            UUID.randomUUID(), caseId, "1003000222", "2026-02-28"));

    assertThat(RevalidationFixtures.caseStatus(jdbc, caseId)).isEqualTo("IN_SCREENING");
  }

  @ParameterizedTest(name = "[MAM-49 AC-1] the RevalidationDue of a {0} case does not change it")
  @ValueSource(strings = {"IN_SCREENING", "COMPLETED", "CANCELLED"})
  void revalidationDueOnlyMovesDueCases(String status) {
    UUID caseId = RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, status);

    revalidationEvents.onEvent(
        RevalidationFixtures.revalidationDue(
            UUID.randomUUID(), caseId, "1003000222", "2026-02-28"));

    assertThat(RevalidationFixtures.caseStatus(jdbc, caseId)).isEqualTo(status);
  }

  @Test
  @DisplayName("[MAM-49 AC-1] the RevalidationDue of an unknown case is ignored without error")
  void revalidationDueOfAnUnknownCase() {
    UUID id = UUID.randomUUID();

    revalidationEvents.onEvent(
        RevalidationFixtures.revalidationDue(id, UUID.randomUUID(), "1003000222", "2026-02-28"));

    assertThat(processed(id)).isEqualTo(1);
    assertThat(RevalidationFixtures.casesOf(jdbc, "1003000222")).isZero();
  }

  @Test
  @DisplayName("[MAM-49 AC-1] an event of another type, or without a case id, changes nothing")
  void otherRevalidationEventsAreIgnored() {
    UUID caseId = RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "DUE");
    Map<String, Object> noCaseId = new LinkedHashMap<>();
    noCaseId.put("npi", "1003000222");
    Map<String, Object> other = new LinkedHashMap<>();
    other.put("caseId", caseId.toString());

    revalidationEvents.onEvent(
        RevalidationFixtures.envelope(
            UUID.randomUUID(),
            "RevalidationDue",
            "revalidation-service",
            "1003000222",
            noCaseId,
            UUID.randomUUID().toString()));
    revalidationEvents.onEvent(
        RevalidationFixtures.envelope(
            UUID.randomUUID(),
            "RevalidationCancelled",
            "revalidation-service",
            "1003000222",
            other,
            UUID.randomUUID().toString()));

    assertThat(RevalidationFixtures.caseStatus(jdbc, caseId)).isEqualTo("DUE");
  }

  @Test
  @DisplayName("[MAM-49 AC-1] an event id is applied once, and a failed step leaves no marker")
  void revalidationDueIsAtomicAndIdempotent() {
    UUID id = UUID.randomUUID();
    UUID caseId = RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "DUE");
    String message = RevalidationFixtures.revalidationDue(id, caseId, "1003000222", "2026-02-28");
    RevalidationFixtures.reject(
        jdbc, "stage", "UPDATE", "revalidation_case", "true", "SYNTHETIC DISK FULL");

    assertThatThrownBy(() -> revalidationEvents.onEvent(message))
        .isInstanceOf(RuntimeException.class);

    assertThat(processed(id)).isZero();
    assertThat(RevalidationFixtures.caseStatus(jdbc, caseId)).isEqualTo("DUE");

    RevalidationFixtures.dropFailures(jdbc);
    revalidationEvents.onEvent(message);
    revalidationEvents.onEvent(message);

    assertThat(RevalidationFixtures.caseStatus(jdbc, caseId)).isEqualTo("IN_SCREENING");
    assertThat(processed(id)).isEqualTo(1);
  }
}
