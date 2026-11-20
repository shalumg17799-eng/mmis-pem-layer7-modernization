package gov.medicaid.pem.revalidation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gov.medicaid.pem.revalidation.EventsContractFile;
import gov.medicaid.pem.revalidation.IntegrationTest;
import gov.medicaid.pem.revalidation.RevalidationFixtures;
import gov.medicaid.pem.testsupport.PemContainers;
import gov.medicaid.pem.testsupport.PemJwt;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * The RevalidationDue events of a sweep against modern/contracts/asyncapi/events.yaml
 * (RevalidationDueEnvelope, lesson f of the earlier reviews): the envelope fields are all there
 * and typed as the contract says, no member is null where the contract says string, the subject and
 * the Kafka message key are the NPI, and the data holds caseId, npi and dueDate. Both what is in
 * the outbox and what reaches Kafka are checked.
 */
@Timeout(240)
class RevalidationDueEventContractTest extends IntegrationTest {

  static final String SCHEMA = "RevalidationDueEnvelope";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void cleanBefore() {
    RevalidationFixtures.reset(jdbc);
  }

  @AfterEach
  void cleanAfter() {
    RevalidationFixtures.reset(jdbc);
  }

  private static boolean hasNull(JsonNode node) {
    if (node.isNull()) {
      return true;
    }
    for (JsonNode child : node) {
      if (hasNull(child)) {
        return true;
      }
    }
    return false;
  }

  private void twoDueProviders() throws Exception {
    RevalidationFixtures.track(jdbc, "1003000333", "A", LocalDate.of(2026, 3, 31));
    RevalidationFixtures.track(jdbc, "1003000222", "A", LocalDate.of(2026, 2, 28));
    RevalidationFixtures.sweep(mvc, "2026-03-31");
  }

  // ---- what the outbox holds -------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-45 AC-4] every RevalidationDue of a sweep conforms to the contract's RevalidationDueEnvelope")
  void outboxEventsConform() throws Exception {
    twoDueProviders();

    List<JsonNode> events = RevalidationFixtures.outbox(jdbc, "RevalidationDue");

    assertThat(events).hasSize(2);
    for (JsonNode e : events) {
      assertThat(EventsContractFile.violations(SCHEMA, e)).as(e.toString()).isEmpty();
    }
  }

  @Test
  @DisplayName("[MAM-45 AC-4] no member of a RevalidationDue is null (a string member is a string, never null)")
  void noNulls() throws Exception {
    twoDueProviders();

    for (JsonNode e : RevalidationFixtures.outbox(jdbc, "RevalidationDue")) {
      assertThat(hasNull(e)).as(e.toString()).isFalse();
    }
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-4] the envelope: id and correlationId are UUIDs, source revalidation-service, subject the NPI, time ISO-8601")
  void envelopeFields() throws Exception {
    twoDueProviders();

    List<JsonNode> events = RevalidationFixtures.outbox(jdbc, "RevalidationDue");

    assertThat(events).hasSize(2);
    for (JsonNode e : events) {
      assertThat(UUID.fromString(e.path("id").asText())).isNotNull();
      assertThat(UUID.fromString(e.path("correlationId").asText())).isNotNull();
      assertThat(e.path("type").asText()).isEqualTo("RevalidationDue");
      assertThat(e.path("source").asText()).isEqualTo("revalidation-service");
      assertThat(e.path("subject").asText()).isIn("1003000222", "1003000333");
      assertThat(e.path("time").asText())
          .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");
      assertThat(e.path("data").path("npi").asText()).isEqualTo(e.path("subject").asText());
    }
  }

  @Test
  @DisplayName("[MAM-45 AC-4] the data holds the case id of the case just opened, the NPI and its own due date")
  void dataIsTheCase() throws Exception {
    twoDueProviders();

    List<JsonNode> events = RevalidationFixtures.outbox(jdbc, "RevalidationDue");

    for (JsonNode e : events) {
      String npi = e.path("subject").asText();
      UUID caseId =
          jdbc.queryForObject(
              "SELECT public_id FROM revalidation_case WHERE npi = ?", UUID.class, npi);
      String due =
          jdbc.queryForObject(
              "SELECT due_date::text FROM revalidation_case WHERE npi = ?", String.class, npi);
      assertThat(e.path("data").path("caseId").asText()).isEqualTo(caseId.toString());
      assertThat(e.path("data").path("dueDate").asText()).isEqualTo(due);
      assertThat(e.path("data").size()).as("only caseId, npi and dueDate").isEqualTo(3);
    }
    assertThat(events)
        .extracting(e -> e.path("data").path("dueDate").asText())
        .containsExactlyInAnyOrder("2026-02-28", "2026-03-31");
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-4] a request header that is not a UUID (but safe to log) does not make the event's correlationId a non-UUID")
  void correlationIdStaysAUuid() throws Exception {
    RevalidationFixtures.track(jdbc, "1003000222", "A", LocalDate.of(2026, 2, 28));
    mvc.perform(
            MockMvcRequestBuilders.post(RevalidationFixtures.SWEEPS)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-Id", "abc-123")
                .content("{\"businessDate\":\"2026-03-31\"}")
                .with(PemJwt.admin()))
        .andReturn();
    RevalidationFixtures.awaitNoRunningJob(jdbc);

    JsonNode e = RevalidationFixtures.outbox(jdbc, "RevalidationDue").get(0);

    assertThat(EventsContractFile.violations(SCHEMA, e)).as(e.toString()).isEmpty();
  }

  // ---- what reaches Kafka ----------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-45 AC-4] the record on pem.revalidation.events conforms to the contract and its key is the NPI")
  void kafkaRecordConformsAndIsKeyedByNpi() throws Exception {
    twoDueProviders();
    UUID eventId =
        UUID.fromString(
            RevalidationFixtures.outbox(jdbc, "RevalidationDue").get(0).path("id").asText());

    Map<String, Object> cfg =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, PemContainers.KAFKA.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "probe-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    ConsumerRecord<String, String> found = null;
    try (var consumer = new KafkaConsumer<String, String>(cfg)) {
      consumer.subscribe(List.of("pem.revalidation.events"));
      long end = System.nanoTime() + RevalidationFixtures.BOUND.toNanos();
      while (found == null && System.nanoTime() < end) {
        for (var rec : consumer.poll(Duration.ofMillis(250))) {
          if (rec.value().contains(eventId.toString())) {
            found = rec;
          }
        }
      }
    }

    assertThat(found).as("event %s on pem.revalidation.events", eventId).isNotNull();
    JsonNode sent = RevalidationFixtures.read(found.value());
    assertThat(EventsContractFile.violations(SCHEMA, sent)).as(found.value()).isEmpty();
    assertThat(found.key()).isEqualTo(sent.path("subject").asText());
  }

  // ---- the checker itself ----------------------------------------------------------------------

  private JsonNode sample() {
    return RevalidationFixtures.read(
        RevalidationFixtures.revalidationDue(
            UUID.randomUUID(), UUID.randomUUID(), "1003000222", "2026-02-28"));
  }

  @Test
  @DisplayName("[MAM-45 AC-4] the checker accepts a well-formed RevalidationDue")
  void checkerAcceptsAGoodEvent() {
    assertThat(EventsContractFile.violations(SCHEMA, sample())).isEmpty();
  }

  @Test
  @DisplayName("[MAM-45 AC-4] the checker names a null caseId, a missing dueDate and a wrong source")
  void checkerFindsViolations() {
    ObjectNode nullCase = (ObjectNode) sample();
    ((ObjectNode) nullCase.get("data")).putNull("caseId");
    ObjectNode noDue = (ObjectNode) sample();
    ((ObjectNode) noDue.get("data")).remove("dueDate");
    ObjectNode wrongSource = (ObjectNode) sample();
    wrongSource.put("source", "provider-service");

    assertThat(EventsContractFile.violations(SCHEMA, nullCase)).contains("$.data.caseId is null");
    assertThat(EventsContractFile.violations(SCHEMA, noDue)).contains("$.data.dueDate is required");
    assertThat(EventsContractFile.violations(SCHEMA, wrongSource))
        .anyMatch(v -> v.startsWith("$.source"));
  }
}
