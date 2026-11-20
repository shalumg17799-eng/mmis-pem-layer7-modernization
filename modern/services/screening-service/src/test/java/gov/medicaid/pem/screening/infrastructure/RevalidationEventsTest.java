package gov.medicaid.pem.screening.infrastructure;

import static gov.medicaid.pem.screening.TestSources.MOCK;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.screening.EventsContractFile;
import gov.medicaid.pem.screening.Lane3Test;
import gov.medicaid.pem.screening.ScreeningEvents;
import gov.medicaid.pem.screening.ScreeningWorld;
import gov.medicaid.pem.screening.TestSources;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * MAM-28 AC-3 and AC-4 on the real Kafka: RevalidationDue starts one REVALIDATION screening with
 * the provider's current data; a delivery with an event id seen before starts nothing. A failure
 * that retrying cannot cure (an NPI provider-service no longer knows, a message that is no event)
 * is parked on pem.revalidation.events.DLT after the retries and later events are still processed
 * (ADR-003): the old message on that topic of the live stack was exactly this case.
 */
@Timeout(value = 150, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class RevalidationEventsTest extends Lane3Test {

  static final String TOPIC = "pem.revalidation.events";
  static final String DLT = "pem.revalidation.events.DLT";
  static final String NPI = "1003000222";
  static final String GONE = "1003000999";

  void known() {
    ScreeningWorld.holds(ScreeningWorld.individual(NPI, "TESTPROV", "BRAVO", "MD22222", "TX"));
    ScreeningWorld.licence("MD22222", "TX", "ACTIVE", "2029-01", 3);
  }

  // ---- AC-3 -------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-28 AC-3] RevalidationDue starts a REVALIDATION screening and announces it with reason REVALIDATION")
  void dueStartsAScreening() throws Exception {
    known();
    awaitProcessed(revalidationDue(NPI, "2026-02-28"));
    assertThat(
            jdbc.queryForList(
                "SELECT reason || '/' || status || '/' || result FROM screening WHERE npi = ?",
                String.class,
                NPI))
        .containsExactly("REVALIDATION/COMPLETED/PASS");
    JsonNode event = completedEvent(NPI);
    assertThat(event.path("data").path("reason").asText()).isEqualTo("REVALIDATION");
    assertThat(event.path("data").path("result").asText()).isEqualTo("PASS");
    assertThat(EventsContractFile.violations("ScreeningCompletedEnvelope", event)).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-3] the provider's current data is read for the re-screening, and the four sources are asked")
  void currentDataIsRead() throws Exception {
    known();
    awaitProcessed(revalidationDue(NPI, "2026-02-28"));
    assertThat(ScreeningWorld.providerReads(NPI)).isEqualTo(1);
    assertThat(ScreeningWorld.lookups("LICBOARD", "MD22222")).isEqualTo(1);
    assertThat(ScreeningWorld.lookups("OIGLEIE", NPI)).isEqualTo(1);
    assertThat(ScreeningWorld.lookups("SAMGOV", NPI)).isEqualTo(1);
    assertThat(ScreeningWorld.lookups("SSADMF", NPI)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-3] the NPI of the event data is used, and the subject when the data has none")
  void npiFromDataOrSubject() throws Exception {
    known();
    UUID id = UUID.randomUUID();
    send(
        TOPIC,
        NPI,
        ScreeningEvents.envelope(
            id,
            "RevalidationDue",
            "revalidation-service",
            NPI,
            UUID.randomUUID().toString(),
            java.util.Map.of("caseId", UUID.randomUUID().toString(), "dueDate", "2026-02-28")));
    awaitProcessed(id);
    assertThat(screeningCount(NPI)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-3] an event of another type on the revalidation topic is recorded and starts no screening")
  void otherTypesAreIgnored() throws Exception {
    known();
    UUID id = UUID.randomUUID();
    send(
        TOPIC,
        NPI,
        ScreeningEvents.envelope(
            id,
            "RevalidationCaseClosed",
            "revalidation-service",
            NPI,
            UUID.randomUUID().toString(),
            java.util.Map.of("npi", NPI)));
    awaitProcessed(id);
    assertThat(screeningCount(NPI)).isZero();
    assertThat(completedEvents(NPI)).isZero();
    assertThat(ScreeningWorld.providerReads(NPI)).isZero();
  }

  // ---- AC-4 -------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-28 AC-4] the same event id delivered twice starts exactly one screening and one ScreeningCompleted")
  void sameEventTwice() throws Exception {
    known();
    UUID id = UUID.randomUUID();
    String corr = UUID.randomUUID().toString();
    ScreeningEvents.revalidationDue(id, NPI, "2026-02-28", corr);
    awaitProcessed(id);
    ScreeningEvents.revalidationDue(id, NPI, "2026-02-28", corr);
    UUID marker = UUID.randomUUID();
    send(
        TOPIC,
        NPI,
        ScreeningEvents.envelope(
            marker, "Marker", "revalidation-service", NPI, corr, java.util.Map.of()));
    awaitProcessed(marker);
    assertThat(screeningCount(NPI)).isEqualTo(1);
    assertThat(completedEvents(NPI)).isEqualTo(1);
    assertThat(ScreeningWorld.providerReads(NPI)).isEqualTo(1);
  }

  @Test
  @DisplayName("[MAM-28 AC-4] two different due events (two cases) start two screenings")
  void twoCases() throws Exception {
    known();
    awaitProcessed(revalidationDue(NPI, "2026-02-28"));
    awaitProcessed(revalidationDue(NPI, "2031-02-28"));
    assertThat(screeningCount(NPI)).isEqualTo(2);
    assertThat(completedEvents(NPI)).isEqualTo(2);
  }

  // ---- the dead-letter topic --------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-28 AC-3] a RevalidationDue for an NPI provider-service no longer knows is parked on the DLT after the retries and later events are still processed")
  void unknownProviderIsParkedAndTheNextEventIsProcessed() throws Exception {
    known();
    ScreeningWorld.providerUnknown(GONE);
    UUID bad = revalidationDue(GONE, "2026-02-28");
    UUID good = revalidationDue(NPI, "2026-02-28");
    String parked = awaitRaw(DLT, text -> text.contains(bad.toString()));
    JsonNode original = json.readTree(parked);
    assertThat(original.path("type").asText()).isEqualTo("RevalidationDue");
    assertThat(original.path("subject").asText()).isEqualTo(GONE);
    assertThat(original.path("data").path("npi").asText()).isEqualTo(GONE);
    awaitProcessed(good);
    assertThat(screeningCount(NPI)).isEqualTo(1);
    // the usual retries: the first attempt and two more (pem.kafka.retries = 2)
    assertThat(ScreeningWorld.providerReads(GONE)).isEqualTo(3);
    assertThat(screeningCount(GONE)).isZero();
    assertThat(completedEvents(GONE)).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM processed_event WHERE event_id = ?", Integer.class, bad))
        .as("a parked event is not recorded as processed")
        .isZero();
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-3] a message on the revalidation topic that is no event is parked on the DLT and later events are still processed")
  void poisonMessageIsParked() throws Exception {
    known();
    String poison = "this is not an event " + UUID.randomUUID();
    send(TOPIC, NPI, poison);
    UUID good = revalidationDue(NPI, "2026-02-28");
    assertThat(awaitRaw(DLT, text -> text.equals(poison))).isEqualTo(poison);
    awaitProcessed(good);
    assertThat(screeningCount(NPI)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-3] an event that failed once and was parked can be delivered again with the same id and is then processed once")
  void parkedEventCanBeDeliveredAgain() throws Exception {
    known();
    UUID id = UUID.randomUUID();
    String corr = UUID.randomUUID().toString();
    ScreeningWorld.providerUnknown(NPI);
    ScreeningEvents.revalidationDue(id, NPI, "2026-02-28", corr);
    awaitRaw(DLT, text -> text.contains(id.toString()));
    assertThat(screeningCount(NPI)).isZero();
    back();
    ScreeningEvents.revalidationDue(id, NPI, "2026-02-28", corr);
    awaitProcessed(id);
    assertThat(screeningCount(NPI)).isEqualTo(1);
    assertThat(completedEvents(NPI)).isEqualTo(1);
  }

  /** Puts the provider back after a test made it unknown. */
  private void back() {
    MOCK.resetAll();
    TestSources.stubToken();
    ScreeningWorld.cleanSources();
    known();
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-3] the DLT holds the original envelope with the event id of the failed delivery")
  void dltHoldsTheOriginal() throws Exception {
    ScreeningWorld.providerUnknown(GONE);
    UUID bad = revalidationDue(GONE, "2026-02-28");
    String parked = awaitRaw(DLT, text -> text.contains(bad.toString()));
    assertThat(json.readTree(parked).path("id").asText()).isEqualTo(bad.toString());
    for (String member :
        List.of("id", "type", "source", "time", "subject", "correlationId", "data")) {
      assertThat(json.readTree(parked).has(member)).as(member).isTrue();
    }
  }
}
