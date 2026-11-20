package gov.medicaid.pem.enrollment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.enrollment.AppInputFile;
import gov.medicaid.pem.enrollment.EventsContractFile;
import gov.medicaid.pem.enrollment.IntakeApi;
import gov.medicaid.pem.enrollment.IntakeIntegrationTest;
import gov.medicaid.pem.testsupport.EventProbe;
import gov.medicaid.pem.testsupport.PemContainers;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MAM-56 AC-3 and AC-4 with the code-review lesson on events: what the intake writes to the outbox
 * is the contract's ApplicationValidatedEnvelope and ApplicationRejectedEnvelope (modern/contracts/
 * asyncapi/events.yaml): no null where the contract says string, no member outside the contract, a
 * uuid correlation id, the NPI as subject. The events of a file record also reach Kafka keyed by
 * the NPI.
 */
class IntakeEventContractTest extends IntakeIntegrationTest {

  static final String BASELINE = "fixed-width/appinput-baseline.dat";
  static final String SCENARIOS = "scenarios/appinput-scenarios.dat";

  List<String> violations(String schema, String type) throws Exception {
    List<String> all = new java.util.ArrayList<>();
    for (JsonNode event : events(type)) {
      EventsContractFile.violations(schema, event)
          .forEach(v -> all.add(event.path("subject").asText() + " " + v));
    }
    return all;
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-4] the baseline file: its three ApplicationValidated events are valid ApplicationValidatedEnvelopes")
  void baselineValidatedEvents() throws Exception {
    IntakeApi.runToEnd(mvc, json, AppInputFile.dataset(BASELINE), "2026-01-15");
    assertThat(events("ApplicationValidated")).hasSize(3);
    assertThat(violations("ApplicationValidatedEnvelope", "ApplicationValidated")).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-4] the baseline file: its four ApplicationRejected events are valid ApplicationRejectedEnvelopes")
  void baselineRejectedEvents() throws Exception {
    IntakeApi.runToEnd(mvc, json, AppInputFile.dataset(BASELINE), "2026-01-15");
    assertThat(events("ApplicationRejected")).hasSize(4);
    assertThat(violations("ApplicationRejectedEnvelope", "ApplicationRejected")).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-3] the scenarios file: its eight ApplicationValidated events are valid ApplicationValidatedEnvelopes")
  void scenariosEvents() throws Exception {
    IntakeApi.runToEnd(mvc, json, AppInputFile.dataset(SCENARIOS), "2026-01-15");
    assertThat(events("ApplicationValidated")).hasSize(8);
    assertThat(violations("ApplicationValidatedEnvelope", "ApplicationValidated")).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-3] a record with a blank Medicaid ID gives an event without the member, not medicaidId null")
  void blankMedicaidIdIsOmitted() throws Exception {
    String record =
        AppInputFile.individual(
            "1003000111", "", "IN", "207Q00000X", "RIVERA", "MARIA", "MD12345", "TX");
    IntakeApi.runToEnd(mvc, json, AppInputFile.lines(record), DATE);
    JsonNode data = events("ApplicationValidated").get(0).path("data");
    assertThat(data.has("medicaidId")).isFalse();
    assertThat(violations("ApplicationValidatedEnvelope", "ApplicationValidated")).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-3] an organisation with no DBA name and no owner gives an event whose organization has only the legal name")
  void organizationMembersAreOmitted() throws Exception {
    String record =
        AppInputFile.organization(
            "1003000333", "M00000333", "FA", "251G00000X", "SUNSET HOSPICE INC", "", "");
    IntakeApi.runToEnd(mvc, json, AppInputFile.lines(record), DATE);
    JsonNode organization = events("ApplicationValidated").get(0).path("data").path("organization");
    assertThat(organization.fieldNames()).toIterable().containsExactly("legalName");
    assertThat(violations("ApplicationValidatedEnvelope", "ApplicationValidated")).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-3] an individual with no name and no licence gives an event whose individual has no null member")
  void individualMembersAreOmitted() throws Exception {
    String record =
        AppInputFile.individual("1003000444", "M00000444", "IN", "207Q00000X", "", "", "", "");
    IntakeApi.runToEnd(mvc, json, AppInputFile.lines(record), DATE);
    assertThat(violations("ApplicationValidatedEnvelope", "ApplicationValidated")).isEmpty();
    assertThat(events("ApplicationValidated").get(0).path("data").toString())
        .doesNotContain("null");
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-4] every event of a file record has the NPI as subject, source enrollment-service and the type of its topic message")
  void envelopeFields() throws Exception {
    IntakeApi.runToEnd(
        mvc,
        json,
        AppInputFile.lines(AppInputFile.valid("1003000111"), AppInputFile.noTaxonomy("1003000222")),
        DATE);
    assertThat(events("ApplicationValidated").get(0).path("subject").asText())
        .isEqualTo("1003000111");
    assertThat(events("ApplicationRejected").get(0).path("subject").asText())
        .isEqualTo("1003000222");
    assertThat(events("ApplicationValidated").get(0).path("source").asText())
        .isEqualTo("enrollment-service");
    assertThat(
            events("ApplicationRejected")
                .get(0)
                .path("data")
                .path("errors")
                .get(0)
                .path("code")
                .asText())
        .isEqualTo("E104");
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-4] the ApplicationValidated event of a file record reaches Kafka on pem.enrollment.events with the NPI as subject")
  void reachesKafka() throws Exception {
    IntakeApi.runToEnd(mvc, json, AppInputFile.lines(AppInputFile.valid("1003000111")), DATE);
    String applicationId =
        jdbc.queryForObject(
            "SELECT public_id::text FROM application WHERE npi = '1003000111'", String.class);
    JsonNode event =
        EventProbe.await(
            PemContainers.KAFKA.getBootstrapServers(),
            "pem.enrollment.events",
            n ->
                n.path("type").asText().equals("ApplicationValidated")
                    && n.path("data").path("applicationId").asText().equals(applicationId),
            Duration.ofSeconds(30));
    assertThat(event.path("subject").asText()).isEqualTo("1003000111");
  }
}
