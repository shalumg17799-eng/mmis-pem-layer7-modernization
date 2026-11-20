package gov.medicaid.pem.screening.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gov.medicaid.pem.screening.EventsContractFile;
import gov.medicaid.pem.screening.Lane3Test;
import gov.medicaid.pem.screening.ScreeningWorld;
import gov.medicaid.pem.testsupport.EventProbe;
import gov.medicaid.pem.testsupport.PemContainers;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Lessons f and g (code reviews of 2026-11-19): the ScreeningCompleted events screening-service
 * writes to the outbox are valid ScreeningCompletedEnvelope documents of
 * modern/contracts/asyncapi/events.yaml, whichever way the screening started (registration,
 * request, revalidation, batch run) and whatever the sources answered; no member is null where the
 * contract says string; and a correlation id taken from an incoming event is never carried into an
 * outgoing event unsanitised.
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ScreeningEventContractTest extends Lane3Test {

  static final String NPI = "1003000111";
  static final String ORG = "1003000444";

  void individual() {
    ScreeningWorld.holds(ScreeningWorld.individual(NPI, "TESTPROV", "ALPHA", "MD12345", "TX"));
    ScreeningWorld.licence("MD12345", "TX", "ACTIVE", "2028-09", 3);
  }

  void assertValid(JsonNode event) {
    assertThat(EventsContractFile.violations("ScreeningCompletedEnvelope", event)).isEmpty();
  }

  // ---- the usual events -------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-25 AC-5] the INITIAL ScreeningCompleted of a registration is a valid envelope of events.yaml (lesson f)")
  void initial() throws Exception {
    individual();
    awaitProcessed(providerRegistered(NPI));
    JsonNode e = completedEvent(NPI);
    assertValid(e);
    assertThat(e.path("type").asText()).isEqualTo("ScreeningCompleted");
    assertThat(e.path("source").asText()).isEqualTo("screening-service");
    assertThat(e.path("subject").asText()).isEqualTo(NPI);
    assertThat(e.path("data").path("npi").asText()).isEqualTo(NPI);
    assertThat(e.path("data").path("sources")).hasSize(4);
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-1] the MANUAL ScreeningCompleted of a request is a valid envelope of events.yaml (lesson f)")
  void manual() throws Exception {
    individual();
    JsonNode screening = screen(NPI);
    JsonNode e = completedEvent(NPI);
    assertValid(e);
    assertThat(e.path("data").path("reason").asText()).isEqualTo("MANUAL");
    assertThat(e.path("data").path("screeningId").asText())
        .isEqualTo(screening.path("screeningId").asText());
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-3] the REVALIDATION ScreeningCompleted is a valid envelope of events.yaml (lesson f)")
  void revalidation() throws Exception {
    individual();
    awaitProcessed(revalidationDue(NPI, "2026-02-28"));
    assertValid(completedEvent(NPI));
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-2] the INITIAL ScreeningCompleted of a batch run is a valid envelope of events.yaml (lesson f)")
  void batchRun() throws Exception {
    ScreeningWorld.lists(
        List.of(
            ScreeningWorld.withFlag(
                ScreeningWorld.individual(NPI, "TESTPROV", "ALPHA", "MD12345", "TX"), "U")));
    runToEnd("2026-03-31");
    JsonNode e = completedEvent(NPI);
    assertValid(e);
    assertThat(e.path("data").path("reason").asText()).isEqualTo("INITIAL");
  }

  @Test
  @DisplayName(
      "[MAM-22 AC-1] the ScreeningCompleted of an organisation with an excluded owner is a valid envelope: result FAIL, the OIGLEIE source with its detail (lesson f)")
  void organisation() throws Exception {
    ScreeningWorld.holds(
        ScreeningWorld.organization(ORG, "GR", "EXCLUDED CLINIC LLC", "1990000111"));
    ScreeningWorld.leieListed("1990000111");
    awaitProcessed(providerRegistered(ORG));
    JsonNode e = completedEvent(ORG);
    assertValid(e);
    assertThat(e.path("data").path("result").asText()).isEqualTo("FAIL");
    assertThat(e.path("data").path("taxonomy").asText()).isEqualTo("251G00000X");
    JsonNode leie = null;
    for (JsonNode s : e.path("data").path("sources")) {
      if (s.path("source").asText().equals("OIGLEIE")) {
        leie = s;
      }
    }
    assertThat(leie).isNotNull();
    assertThat(leie.path("result").asText()).isEqualTo("F");
    assertThat(leie.path("detail").asText()).contains("1990000111");
  }

  @Test
  @DisplayName(
      "[MAM-20 AC-1] the ScreeningCompleted of a screening whose four sources gave no answer is a valid envelope: four C, every detail a string (lesson f)")
  void allSourcesDown() throws Exception {
    individual();
    ScreeningWorld.sourceError("LICBOARD", "MD12345", 500);
    ScreeningWorld.sourceError("OIGLEIE", NPI, 500);
    ScreeningWorld.sourceError("SAMGOV", NPI, 503);
    ScreeningWorld.sourceError("SSADMF", NPI, 500);
    awaitProcessed(providerRegistered(NPI));
    JsonNode e = completedEvent(NPI);
    assertValid(e);
    assertThat(e.path("data").path("result").asText()).isEqualTo("FAIL");
    for (JsonNode s : e.path("data").path("sources")) {
      assertThat(s.path("result").asText()).isEqualTo("C");
      assertThat(s.path("detail").isTextual()).isTrue();
      assertThat(s.path("detail").asText()).isEqualTo("COULD NOT BE REACHED");
    }
  }

  @Test
  @DisplayName(
      "[MAM-25 AC-5] the event written to the outbox is the event Kafka carries, with the NPI as key")
  void relayedEventIsTheStoredEvent() throws Exception {
    individual();
    awaitProcessed(providerRegistered(NPI));
    JsonNode stored = completedEvent(NPI);
    JsonNode published =
        EventProbe.await(
            PemContainers.KAFKA.getBootstrapServers(),
            "pem.screening.events",
            n -> stored.path("id").asText().equals(n.path("id").asText()),
            BOUND);
    assertThat(published.path("subject").asText()).isEqualTo(NPI);
    assertThat(published.path("data")).isEqualTo(stored.path("data"));
    assertValid(published);
  }

  @Test
  @DisplayName(
      "[MAM-25 AC-5] a provider record without a taxonomy gives an event without a taxonomy member, never taxonomy null (lesson f)")
  void missingTaxonomyIsOmitted() throws Exception {
    ObjectNode p = ScreeningWorld.individual(NPI, "TESTPROV", "ALPHA", "MD12345", "TX");
    p.remove("taxonomy");
    ScreeningWorld.holds(p);
    ScreeningWorld.licence("MD12345", "TX", "ACTIVE", "2028-09", 3);
    awaitProcessed(providerRegistered(NPI));
    JsonNode e = completedEvent(NPI);
    assertThat(EventsContractFile.violations("ScreeningCompletedEnvelope", e)).isEmpty();
  }

  // ---- lesson g: the correlation id of an incoming event ----------------------------------

  @Test
  @DisplayName(
      "[MAM-18 AC-1] the correlation id of a valid incoming event is the correlation id of the ScreeningCompleted it causes")
  void correlationIdFollowsTheEvent() throws Exception {
    individual();
    String corr = UUID.randomUUID().toString();
    awaitProcessed(providerRegistered(UUID.randomUUID(), NPI, corr));
    assertThat(completedEvent(NPI).path("correlationId").asText()).isEqualTo(corr);
  }

  @ParameterizedTest(
      name =
          "[MAM-18 AC-1] the hostile correlation id number {index} of an incoming event is not carried into the ScreeningCompleted (lesson g)")
  @ValueSource(
      strings = {
        "bad id with spaces",
        "x<script>alert(1)</script>",
        "semi;colon",
        "FORGED-LOG-LINE ERROR something terrible happened",
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "line\nbreak",
        ""
      })
  void hostileCorrelationIdIsReplaced(String hostile) throws Exception {
    individual();
    awaitProcessed(providerRegistered(UUID.randomUUID(), NPI, hostile));
    JsonNode e = completedEvent(NPI);
    String out = e.path("correlationId").asText();
    assertThat(out).isNotEqualTo(hostile).matches("[A-Za-z0-9._:-]{1,64}");
    assertValid(e);
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-3] the hostile correlation id of a RevalidationDue is not carried into the ScreeningCompleted (lesson g)")
  void hostileCorrelationIdOnRevalidation() throws Exception {
    individual();
    awaitProcessed(
        revalidationDue(UUID.randomUUID(), NPI, "2026-02-28", "FORGED-LOG-LINE <b>x</b> y"));
    String out = completedEvent(NPI).path("correlationId").asText();
    assertThat(out).doesNotContain("FORGED").matches("[A-Za-z0-9._:-]{1,64}");
  }
}
