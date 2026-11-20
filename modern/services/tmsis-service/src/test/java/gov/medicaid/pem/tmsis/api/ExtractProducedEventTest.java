package gov.medicaid.pem.tmsis.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.testsupport.PemJwt;
import gov.medicaid.pem.tmsis.EventsContractFile;
import gov.medicaid.pem.tmsis.IntegrationTest;
import gov.medicaid.pem.tmsis.KafkaEvents;
import gov.medicaid.pem.tmsis.KafkaEvents.Message;
import gov.medicaid.pem.tmsis.LogCapture;
import gov.medicaid.pem.tmsis.TestProviderService;
import gov.medicaid.pem.tmsis.TestProviderService.Fault;
import gov.medicaid.pem.tmsis.TestProviderService.Prov;
import gov.medicaid.pem.tmsis.TmsisApi;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * MAM-75 AC-5: ExtractProduced is published when the extract is archived, exactly once, keyed by
 * the run id (Q-061 answer B), only after the run is COMPLETED and archived; and a failed run
 * announces nothing. The event written to the outbox conforms to events.yaml (no null where the
 * contract says string), and a correlation id taken from a request header reaches neither the log
 * nor the event unsanitised.
 */
@Timeout(240)
class ExtractProducedEventTest extends IntegrationTest {

  static final String DATE = "2026-03-31";
  static final Duration FIND = Duration.ofSeconds(40);
  static final Duration SETTLE = Duration.ofSeconds(3);
  static final String UUID_FORMAT =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

  static void twoActive() {
    TestProviderService.holds(
        Prov.active("1003000333"), Prov.active("1003000111"), Prov.of("1003000444", "D"));
  }

  /** The one ExtractProduced envelope of the run in the outbox. */
  JsonNode envelopeOf(String runId) throws Exception {
    List<String> rows =
        jdbc.queryForList(
            "SELECT payload::text FROM outbox_event WHERE type = 'ExtractProduced' AND subject = ?",
            String.class,
            runId);
    assertThat(rows).as("ExtractProduced events of run " + runId).hasSize(1);
    return json.readTree(rows.get(0));
  }

  // ---- the event ---------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-75 AC-5] a run for 2026-03-31 that wrote 2 rows puts exactly one ExtractProduced in the outbox: source tmsis-service, data.runId, businessDate 2026-03-31, periodYyddd 26090, rowCount 2")
  void oneEventWithTheRunsData() throws Exception {
    twoActive();
    JsonNode run = api.runToEnd(DATE);
    String runId = run.path("runId").asText();
    assertThat(run.path("counts").path("rows").asInt()).isEqualTo(2);
    JsonNode event = envelopeOf(runId);
    assertThat(event.path("type").asText()).isEqualTo("ExtractProduced");
    assertThat(event.path("source").asText()).isEqualTo("tmsis-service");
    assertThat(event.path("data").path("runId").asText()).isEqualTo(runId);
    assertThat(event.path("data").path("businessDate").asText()).isEqualTo(DATE);
    assertThat(event.path("data").path("periodYyddd").asText()).isEqualTo("26090");
    assertThat(event.path("data").path("rowCount").asInt()).isEqualTo(2);
    assertThat(extractProducedEnvelopes()).hasSize(1);
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-5] the event's subject is the run id, equal to data.runId (Q-061 B), not an NPI")
  void subjectIsTheRunId() throws Exception {
    twoActive();
    String runId = api.runToEnd(DATE).path("runId").asText();
    JsonNode event = envelopeOf(runId);
    assertThat(event.path("subject").asText()).isEqualTo(runId);
    assertThat(event.path("subject").asText()).isEqualTo(event.path("data").path("runId").asText());
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-5] the event is published on pem.tmsis.events through Kafka exactly once, with the run id as the message key")
  void publishedOnceOnKafkaKeyedByTheRunId() throws Exception {
    twoActive();
    String runId = api.runToEnd(DATE).path("runId").asText();
    List<Message> messages = KafkaEvents.extractProduced(runId, FIND, SETTLE);
    assertThat(messages).hasSize(1);
    assertThat(messages.get(0).key()).isEqualTo(runId);
    assertThat(messages.get(0).event().path("source").asText()).isEqualTo("tmsis-service");
    assertThat(messages.get(0).event().path("data").path("rowCount").asInt()).isEqualTo(2);
    assertThat(messages.get(0).event().path("data").path("periodYyddd").asText())
        .isEqualTo("26090");
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-5] when the event can be read the run is already COMPLETED and archived (published only after)")
  void publishedOnlyAfterTheRunIsCompletedAndArchived() throws Exception {
    twoActive();
    String runId = api.start(DATE).path("runId").asText();
    List<Message> messages = KafkaEvents.extractProduced(runId, FIND, Duration.ZERO);
    assertThat(messages).as("the event of run " + runId).hasSize(1);
    JsonNode listed = api.listed(runId);
    assertThat(listed.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(listed.path("counts").path("archiveGeneration").asInt()).isEqualTo(1);
    assertThat(api.fileStatus(runId)).isEqualTo(200);
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-5] a run that wrote no rows is announced too, with rowCount 0")
  void emptyRunIsAnnounced() throws Exception {
    TestProviderService.holds();
    String runId = api.runToEnd(DATE).path("runId").asText();
    assertThat(envelopeOf(runId).path("data").path("rowCount").asInt(-1)).isZero();
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-5] two runs are two events, each with its own run id as subject and in data")
  void oneEventPerRun() throws Exception {
    twoActive();
    String a = api.runToEnd("2026-02-28").path("runId").asText();
    String b = api.runToEnd(DATE).path("runId").asText();
    assertThat(extractProducedEnvelopes()).hasSize(2);
    assertThat(envelopeOf(a).path("data").path("periodYyddd").asText()).isEqualTo("26059");
    assertThat(envelopeOf(b).path("data").path("periodYyddd").asText()).isEqualTo("26090");
  }

  // ---- a failed run announces nothing -----------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-75 AC-6] a run that fails because provider-service fails publishes no ExtractProduced: none in the outbox, none on Kafka")
  void failedRunAnnouncesNothing() throws Exception {
    twoActive();
    TestProviderService.providerFails(Fault.SERVER_ERROR);
    JsonNode run = api.runToEnd(DATE);
    String runId = run.path("runId").asText();
    assertThat(run.path("status").asText()).isEqualTo("FAILED");
    assertThat(extractProducedEnvelopes()).isEmpty();
    assertThat(KafkaEvents.extractProduced(runId, Duration.ofSeconds(6), Duration.ZERO)).isEmpty();
  }

  // ---- lesson f: the event conforms to events.yaml ----------------------------------------------

  @Test
  @DisplayName(
      "[MAM-75 AC-5] the stored event is a valid ExtractProducedEnvelope of events.yaml (code review 2026-11-19, lesson f)")
  void eventConformsToTheContract() throws Exception {
    twoActive();
    String runId = api.runToEnd(DATE).path("runId").asText();
    assertThat(EventsContractFile.violations("ExtractProducedEnvelope", envelopeOf(runId)))
        .isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-5] the event of a run with no rows is a valid ExtractProducedEnvelope too: no null member")
  void emptyEventConformsToTheContract() throws Exception {
    TestProviderService.holds();
    String runId = api.runToEnd(DATE).path("runId").asText();
    assertThat(EventsContractFile.violations("ExtractProducedEnvelope", envelopeOf(runId)))
        .isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-5] the event Kafka delivers is a valid ExtractProducedEnvelope, and its key is its subject")
  void kafkaEventConformsToTheContract() throws Exception {
    twoActive();
    String runId = api.runToEnd(DATE).path("runId").asText();
    List<Message> messages = KafkaEvents.extractProduced(runId, FIND, Duration.ZERO);
    assertThat(messages).hasSize(1);
    assertThat(EventsContractFile.violations("ExtractProducedEnvelope", messages.get(0).event()))
        .isEmpty();
    assertThat(messages.get(0).key()).isEqualTo(messages.get(0).event().path("subject").asText());
  }

  // ---- lesson g: a correlation id from a header is sanitised ------------------------------------

  MvcResult startWithCorrelationId(String correlationId) throws Exception {
    return mvc.perform(
            MockMvcRequestBuilders.post(TmsisApi.BASE)
                .with(PemJwt.admin())
                .header("X-Correlation-Id", correlationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(TmsisApi.body(DATE)))
        .andReturn();
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-5] a correlation id with a line break and other unsafe characters is replaced: it is not echoed, not logged and not in the event (code review 2026-11-19, lesson g)")
  void unsafeCorrelationIdNeverReachesLogOrEvent() throws Exception {
    twoActive();
    String evil = "abc\r\nFORGED-LOG-LINE level=ERROR <script>";
    String runId;
    try (LogCapture log = LogCapture.open()) {
      MvcResult r = startWithCorrelationId(evil);
      assertThat(r.getResponse().getStatus()).isEqualTo(202);
      String echoed = r.getResponse().getHeader("X-Correlation-Id");
      assertThat(echoed).isNotNull().doesNotContain("FORGED").doesNotContain("\n");
      assertThat(echoed).matches(UUID_FORMAT);
      runId = api.read(r).path("runId").asText();
      api.await(runId);
      assertThat(log.lines())
          .extracting(LogCapture.Line::message)
          .noneMatch(m -> m.contains("FORGED"));
      assertThat(log.lines())
          .extracting(l -> String.valueOf(l.correlationId()))
          .noneMatch(c -> c.contains("FORGED") || c.contains("\n"));
    }
    JsonNode event = envelopeOf(runId);
    assertThat(event.path("correlationId").asText()).doesNotContain("FORGED").matches(UUID_FORMAT);
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-5] a safe correlation id is echoed in the answer, and the event's correlationId is a uuid as the contract says (lesson f and g)")
  void safeCorrelationIdKeepsTheEventValid() throws Exception {
    twoActive();
    MvcResult r = startWithCorrelationId("my-trace.42");
    assertThat(r.getResponse().getHeader("X-Correlation-Id")).isEqualTo("my-trace.42");
    String runId = api.read(r).path("runId").asText();
    api.await(runId);
    JsonNode event = envelopeOf(runId);
    assertThat(UUID.fromString(event.path("correlationId").asText())).isNotNull();
    assertThat(EventsContractFile.violations("ExtractProducedEnvelope", event)).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-5] a request without a correlation id gets one generated and echoed in the answer")
  void correlationIdGenerated() throws Exception {
    twoActive();
    MvcResult r = api.postBody(PemJwt.admin(), TmsisApi.body(DATE));
    assertThat(r.getResponse().getHeader("X-Correlation-Id")).matches(UUID_FORMAT);
    api.await(api.read(r).path("runId").asText());
  }
}
