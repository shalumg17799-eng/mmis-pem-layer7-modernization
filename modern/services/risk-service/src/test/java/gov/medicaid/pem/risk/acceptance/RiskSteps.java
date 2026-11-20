package gov.medicaid.pem.risk.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.testsupport.PemContainers;
import gov.medicaid.pem.testsupport.PemJwt;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Steps for MAM-35 (risk tier from the taxonomy prefix after a passed screening). */
public class RiskSteps {

  private static final String METRIC = "/actuator/metrics/pem.risk.tiers.assigned";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private final ListAppender<ILoggingEvent> log = new ListAppender<>();
  private double assignedBefore;
  private String lastNpi;
  private String lastEnvelope;
  private UUID lastId;
  private String lastCorrelation;

  @Before("@MAM-35")
  public void reset() throws Exception {
    restoreTable();
    jdbc.update("DELETE FROM outbox_event");
    jdbc.update("DELETE FROM risk_assessment");
    log.start();
    ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).addAppender(log);
    assignedBefore = assigned();
  }

  @After("@MAM-35")
  public void cleanUp() {
    restoreTable();
    ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).detachAppender(log);
  }

  private void restoreTable() {
    Integer off =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'risk_assessment_off'",
            Integer.class);
    if (off != null && off > 0) {
      jdbc.execute("ALTER TABLE risk_assessment_off RENAME TO risk_assessment");
    }
  }

  private String envelope(
      UUID id, String npi, String result, String reason, String taxonomy, String correlation)
      throws Exception {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("screeningId", UUID.randomUUID().toString());
    data.put("npi", npi);
    data.put("reason", reason);
    data.put("result", result);
    data.put("taxonomy", taxonomy);
    data.put("sources", List.of());
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("id", id.toString());
    e.put("type", "ScreeningCompleted");
    e.put("source", "screening-service");
    e.put("time", "2026-11-18T10:00:00Z");
    e.put("subject", npi);
    e.put("correlationId", correlation);
    e.put("data", data);
    return json.writeValueAsString(e);
  }

  private static void publish(String key, String value) throws Exception {
    Map<String, Object> cfg =
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, PemContainers.KAFKA.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    try (var producer = new KafkaProducer<String, String>(cfg)) {
      producer.send(new ProducerRecord<>("pem.screening.events", key, value)).get();
    }
  }

  private void awaitProcessed(UUID id) throws Exception {
    long end = System.nanoTime() + Duration.ofSeconds(45).toNanos();
    while (System.nanoTime() < end) {
      Integer n =
          jdbc.queryForObject(
              "SELECT count(*) FROM processed_event WHERE event_id = ?", Integer.class, id);
      if (n != null && n > 0) {
        return;
      }
      Thread.sleep(200);
    }
    throw new AssertionError("event " + id + " was not processed by risk-service within 45 s");
  }

  private void send(String npi, String result, String reason, String taxonomy) throws Exception {
    lastId = UUID.randomUUID();
    lastNpi = npi;
    lastCorrelation = UUID.randomUUID().toString();
    lastEnvelope = envelope(lastId, npi, result, reason, taxonomy, lastCorrelation);
    publish(npi, lastEnvelope);
  }

  @When(
      "risk-service processes ScreeningCompleted {string} for NPI {string} with taxonomy {string}")
  public void processes(String result, String npi, String taxonomy) throws Exception {
    send(npi, result, "INITIAL", taxonomy);
    awaitProcessed(lastId);
  }

  @When(
      "risk-service processes ScreeningCompleted {string} with reason {string} for NPI {string} with taxonomy {string}")
  public void processesReason(String result, String reason, String npi, String taxonomy)
      throws Exception {
    send(npi, result, reason, taxonomy);
    awaitProcessed(lastId);
  }

  @When(
      "risk-service processes ScreeningCompleted {string} for NPI {string} with taxonomy {string}, delivered twice with the same event id")
  public void twice(String result, String npi, String taxonomy) throws Exception {
    send(npi, result, "INITIAL", taxonomy);
    awaitProcessed(lastId);
    publish(npi, lastEnvelope);
    UUID marker = UUID.randomUUID();
    publish(npi, envelope(marker, npi, "FAIL", "INITIAL", taxonomy, UUID.randomUUID().toString()));
    awaitProcessed(marker); // same key: the redelivery was handled before the marker
  }

  @Given("the risk assessment table cannot be written")
  public void breakTable() {
    jdbc.execute("ALTER TABLE risk_assessment RENAME TO risk_assessment_off");
  }

  @When("ScreeningCompleted {string} for NPI {string} with taxonomy {string} fails to be processed")
  public void fails(String result, String npi, String taxonomy) throws Exception {
    send(npi, result, "INITIAL", taxonomy);
    long end = System.nanoTime() + Duration.ofSeconds(45).toNanos();
    while (System.nanoTime() < end && !logged(lastCorrelation)) {
      Thread.sleep(200);
    }
  }

  private boolean logged(String correlation) {
    return List.copyOf(log.list).stream()
        .anyMatch(
            e ->
                e.getFormattedMessage().contains(correlation)
                    || correlation.equals(e.getMDCPropertyMap().get("correlationId")));
  }

  @Then("the log holds the failure with the event's correlation id")
  public void failureLogged() {
    assertThat(logged(lastCorrelation))
        .as("a log entry with correlation id %s", lastCorrelation)
        .isTrue();
  }

  @When("the risk assessment table can be written again and the same event is delivered again")
  public void redeliver() throws Exception {
    restoreTable();
    publish(lastNpi, lastEnvelope);
    awaitProcessed(lastId);
  }

  private List<JsonNode> written(String npi) throws Exception {
    List<String> payloads =
        jdbc.queryForList(
            "SELECT payload::text FROM outbox_event WHERE type = 'RiskTierAssigned' AND subject = ? ORDER BY created_at",
            String.class,
            npi);
    List<JsonNode> out = new java.util.ArrayList<>();
    for (String p : payloads) {
      out.add(json.readTree(p));
    }
    return out;
  }

  @Then(
      "{int} RiskTierAssigned event(s) is/are written for NPI {string} with taxonomy {string}, tier {string} and rule set version {int}")
  public void writtenWith(int count, String npi, String taxonomy, String tier, int version)
      throws Exception {
    List<JsonNode> events = written(npi);
    assertThat(events).hasSize(count);
    for (JsonNode e : events) {
      assertThat(e.path("source").asText()).isEqualTo("risk-service");
      JsonNode d = e.path("data");
      assertThat(d.path("npi").asText()).isEqualTo(npi);
      assertThat(d.path("taxonomy").asText()).isEqualTo(taxonomy);
      assertThat(d.path("tier").asText()).isEqualTo(tier);
      assertThat(d.path("ruleSetVersion").asInt()).isEqualTo(version);
    }
  }

  @Then(
      "{int} RiskTierAssigned events are written for NPI {string}, each with exactly one tier {string} and rule set version {int}")
  public void each(int count, String npi, String tier, int version) throws Exception {
    List<JsonNode> events = written(npi);
    assertThat(events).hasSize(count);
    for (JsonNode e : events) {
      assertThat(e.path("data").path("tier").isTextual()).isTrue();
      assertThat(e.path("data").path("tier").asText()).isEqualTo(tier);
      assertThat(e.path("data").path("ruleSetVersion").asInt()).isEqualTo(version);
    }
  }

  @Then("{int} RiskTierAssigned events are written for NPI {string}")
  public void none(int count, String npi) throws Exception {
    assertThat(written(npi)).hasSize(count);
  }

  @Then(
      "a RiskTierAssigned event with key {string} reaches topic {string} with tier {string} and rule set version {int}")
  public void onTopic(String key, String topic, String tier, int version) throws Exception {
    Map<String, Object> cfg =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            PemContainers.KAFKA.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG,
            "probe-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class);
    String expectedId = written(key).get(0).path("id").asText();
    JsonNode found = null;
    try (var consumer = new KafkaConsumer<String, String>(cfg)) {
      consumer.subscribe(List.of(topic));
      long end = System.nanoTime() + Duration.ofSeconds(30).toNanos();
      while (System.nanoTime() < end && found == null) {
        for (var rec : consumer.poll(Duration.ofMillis(250))) {
          JsonNode n = json.readTree(rec.value());
          if (key.equals(rec.key()) && n.path("id").asText().equals(expectedId)) {
            found = n;
          }
        }
      }
    }
    assertThat(found).as("RiskTierAssigned for %s on %s", key, topic).isNotNull();
    assertThat(found.path("data").path("tier").asText()).isEqualTo(tier);
    assertThat(found.path("data").path("ruleSetVersion").asInt()).isEqualTo(version);
  }

  private double assigned() throws Exception {
    MvcResult r = mvc.perform(get(METRIC).with(PemJwt.admin())).andReturn();
    if (r.getResponse().getStatus() == 404) {
      return 0;
    }
    return json.readTree(r.getResponse().getContentAsString())
        .path("measurements")
        .get(0)
        .path("value")
        .asDouble();
  }

  @Then("the tiers-assigned counter rose by {int}")
  public void counter(int by) throws Exception {
    assertThat(assigned()).isEqualTo(assignedBefore + by);
  }
}
