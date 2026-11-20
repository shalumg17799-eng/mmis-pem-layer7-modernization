package gov.medicaid.pem.screening.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.screening.Lane3Test;
import gov.medicaid.pem.screening.ScreeningEvents;
import gov.medicaid.pem.screening.ScreeningWorld;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Lesson c (code reviews of 2026-11-19, ADR-003): the processed marker of an event and the work it
 * causes (the screening and its ScreeningCompleted in the outbox) are written in one transaction. A
 * write failure in between must not leave the marker behind: the event that was parked after the
 * failure and is delivered again with the same id is processed, once. Both consumers are checked:
 * ProviderRegistered (MAM-18, INITIAL) and RevalidationDue (MAM-28, REVALIDATION).
 */
@Timeout(value = 150, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class EventTransactionTest extends Lane3Test {

  static final String NPI = "1003000111";

  /** The event that is sent. */
  enum Kind {
    PROVIDER_REGISTERED("pem.provider.events", "INITIAL"),
    REVALIDATION_DUE("pem.revalidation.events", "REVALIDATION");

    final String topic;
    final String reason;

    Kind(String topic, String reason) {
      this.topic = topic;
      this.reason = reason;
    }
  }

  void world() {
    ScreeningWorld.holds(ScreeningWorld.individual(NPI, "TESTPROV", "ALPHA", "MD12345", "TX"));
    ScreeningWorld.licence("MD12345", "TX", "ACTIVE", "2028-09", 3);
  }

  void send(Kind kind, UUID id, String correlationId) {
    if (kind == Kind.PROVIDER_REGISTERED) {
      ScreeningEvents.providerRegistered(id, NPI, correlationId);
    } else {
      ScreeningEvents.revalidationDue(id, NPI, "2026-02-28", correlationId);
    }
  }

  @ParameterizedTest(
      name =
          "[MAM-28 AC-4] {0}: when the screening cannot be written, the event is parked, not recorded as processed, and delivered again it is processed once (lesson c)")
  @EnumSource(Kind.class)
  void markerAndScreeningAreOneTransaction(Kind kind) throws Exception {
    world();
    reject("reject_screening_" + kind.ordinal(), "INSERT", "screening", "NEW.npi = '" + NPI + "'");
    UUID id = UUID.randomUUID();
    String corr = UUID.randomUUID().toString();
    send(kind, id, corr);
    awaitRaw(kind.topic + ".DLT", text -> text.contains(id.toString()));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM processed_event WHERE event_id = ?", Integer.class, id))
        .as("the event is recorded as processed although its screening could not be written")
        .isZero();
    assertThat(screeningCount(NPI)).isZero();
    assertThat(completedEvents(NPI)).isZero();

    lift("reject_screening_" + kind.ordinal());
    send(kind, id, corr);
    awaitProcessed(id);
    assertThat(screeningCount(NPI)).isEqualTo(1);
    assertThat(completedEvents(NPI)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM screening WHERE npi = ? AND reason = ?",
                Integer.class,
                NPI,
                kind.reason))
        .isEqualTo(1);
  }

  @ParameterizedTest(
      name =
          "[MAM-28 AC-4] {0}: when the ScreeningCompleted cannot be written, the screening is rolled back with the marker (lesson c)")
  @EnumSource(Kind.class)
  void screeningAndEventAreOneTransaction(Kind kind) throws Exception {
    world();
    reject(
        "reject_outbox_" + kind.ordinal(), "INSERT", "outbox_event", "NEW.subject = '" + NPI + "'");
    UUID id = UUID.randomUUID();
    String corr = UUID.randomUUID().toString();
    send(kind, id, corr);
    awaitRaw(kind.topic + ".DLT", text -> text.contains(id.toString()));
    assertThat(screeningCount(NPI)).as("a screening kept without its ScreeningCompleted").isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM processed_event WHERE event_id = ?", Integer.class, id))
        .isZero();

    lift("reject_outbox_" + kind.ordinal());
    send(kind, id, corr);
    awaitProcessed(id);
    assertThat(screeningCount(NPI)).isEqualTo(1);
    assertThat(completedEvents(NPI)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-7] a requested screening whose ScreeningCompleted cannot be written is not kept: the screening and its event are one transaction (lesson c)")
  void requestedScreeningAndEventAreOneTransaction() throws Exception {
    world();
    reject("reject_outbox_manual", "INSERT", "outbox_event", "NEW.subject = '" + NPI + "'");
    var r = post("/api/v1/screenings", token("ANALYST"), "{\"npi\":\"" + NPI + "\"}");
    assertThat(r.getResponse().getStatus()).isEqualTo(202);
    until(
        "the background screening has ended (the sources were asked)",
        () -> ScreeningWorld.lookups("SAMGOV", NPI) >= 1,
        BOUND);
    Thread.sleep(1500);
    assertThat(screeningCount(NPI)).isZero();
    assertThat(completedEvents(NPI)).isZero();
  }
}
