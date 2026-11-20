package gov.medicaid.pem.provider.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.testsupport.PemContainers;
import gov.medicaid.pem.testsupport.PemJwt;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Steps for MAM-21 (screen flag from ScreeningCompleted) and MAM-23 (risk tier from
 * RiskTierAssigned).
 */
public class VerdictAndTierSteps {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private String lastTopic;
  private String lastNpi;
  private String lastEnvelope;

  @Given(
      "provider {string} is stored with status {string}, screen flag {string}, risk tier {string} and version {int}")
  public void stored(String npi, String status, String flag, String tier, int version) {
    jdbc.update(
        "DELETE FROM provider_owner WHERE provider_id IN (SELECT id FROM provider WHERE npi = ?)",
        npi);
    jdbc.update("DELETE FROM provider WHERE npi = ?", npi);
    jdbc.update("DELETE FROM outbox_event WHERE subject = ?", npi);
    jdbc.update(
        "INSERT INTO provider (public_id, npi, medicaid_id, provider_type, taxonomy, risk_tier, enroll_status, screen_flag,"
            + " effective_date, reval_due_date, last_name, first_name, license_no, license_state, version)"
            + " VALUES (?, ?, 'MCD000111', 'IN', '207Q00000X', ?, ?, ?, DATE '2026-11-18', ?, 'RIVERA', 'MARIA', 'MD12345',"
            + " 'TX', ?)",
        UUID.randomUUID(),
        npi,
        tier.equals("none") ? null : tier,
        status,
        flag,
        status.equals("A") ? java.sql.Date.valueOf("2031-11-18") : null,
        version);
  }

  private void send(String topic, UUID id, String type, String npi, Map<String, Object> data)
      throws Exception {
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("id", id.toString());
    e.put("type", type);
    e.put("source", topic.equals("pem.risk.events") ? "risk-service" : "screening-service");
    e.put("time", "2026-11-18T10:00:00Z");
    e.put("subject", npi);
    e.put("correlationId", UUID.randomUUID().toString());
    e.put("data", data);
    String envelope = json.writeValueAsString(e);
    publish(topic, npi, envelope);
    lastTopic = topic;
    lastNpi = npi;
    lastEnvelope = envelope;
  }

  private static void publish(String topic, String key, String value) throws Exception {
    Map<String, Object> cfg =
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, PemContainers.KAFKA.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    try (var producer = new KafkaProducer<String, String>(cfg)) {
      producer.send(new ProducerRecord<>(topic, key, value)).get();
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
    throw new AssertionError("event " + id + " was not processed by provider-service within 45 s");
  }

  @When(
      "provider-service consumes a ScreeningCompleted event for {string} with reason {string} and result {string}")
  public void screeningCompleted(String npi, String reason, String result) throws Exception {
    UUID id = UUID.randomUUID();
    List<Map<String, String>> sources = new ArrayList<>();
    for (String s : List.of("LICBOARD", "OIGLEIE", "SAMGOV", "SSADMF")) {
      sources.add(
          Map.of(
              "source",
              s,
              "result",
              result.equals("PASS") ? "P" : (s.equals("OIGLEIE") ? "F" : "P"),
              "detail",
              "X"));
    }
    send(
        "pem.screening.events",
        id,
        "ScreeningCompleted",
        npi,
        Map.of(
            "screeningId", UUID.randomUUID().toString(),
            "npi", npi,
            "reason", reason,
            "result", result,
            "taxonomy", "207Q00000X",
            "sources", sources));
    awaitProcessed(id);
  }

  @When("provider-service consumes a RiskTierAssigned event for {string} with tier {string}")
  public void riskTierAssigned(String npi, String tier) throws Exception {
    UUID id = UUID.randomUUID();
    send(
        "pem.risk.events",
        id,
        "RiskTierAssigned",
        npi,
        Map.of("npi", npi, "taxonomy", "207Q00000X", "tier", tier, "ruleSetVersion", 1));
    awaitProcessed(id);
  }

  @When("the same event is delivered again")
  public void redeliver() throws Exception {
    publish(lastTopic, lastNpi, lastEnvelope);
    UUID marker = UUID.randomUUID();
    send(lastTopic, marker, "TestMarker", lastNpi, Map.of("npi", lastNpi));
    awaitProcessed(marker); // same topic and key: the redelivery was handled before the marker
  }

  private JsonNode provider(String npi) throws Exception {
    MvcResult r =
        mvc.perform(get("/api/v1/providers/{npi}", npi).with(PemJwt.system())).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString());
  }

  @Then(
      "provider {string} has status {string}, screen flag {string}, risk tier {string} and version {int}")
  public void has(String npi, String status, String flag, String tier, int version)
      throws Exception {
    JsonNode p = provider(npi);
    assertThat(p.path("enrollStatus").asText()).isEqualTo(status);
    assertThat(p.path("screenFlag").asText()).isEqualTo(flag);
    if (tier.equals("none")) {
      assertThat(p.path("riskTier").isMissingNode() || p.path("riskTier").isNull()).isTrue();
    } else {
      assertThat(p.path("riskTier").asText()).isEqualTo(tier);
    }
    assertThat(p.path("version").asInt()).isEqualTo(version);
  }

  @Then(
      "searching providers with status {string} and screen flag {string} as ANALYST lists {string}")
  public void search(String status, String flag, String npi) throws Exception {
    MvcResult r =
        mvc.perform(
                get("/api/v1/providers")
                    .param("status", status)
                    .param("screenFlag", flag)
                    .with(PemJwt.analyst()))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    JsonNode page = json.readTree(r.getResponse().getContentAsString());
    List<String> npis = new ArrayList<>();
    page.path("items")
        .forEach(
            i -> {
              npis.add(i.path("npi").asText());
              assertThat(i.path("enrollStatus").asText()).isEqualTo(status);
              assertThat(i.path("screenFlag").asText()).isEqualTo(flag);
            });
    assertThat(npis).contains(npi);
    assertThat(page.path("total").asInt()).isEqualTo(npis.size());
  }

  @Then("no ProviderStatusChanged event is written for {string}")
  public void noStatusChange(String npi) {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE type = 'ProviderStatusChanged' AND subject = ?",
                Integer.class,
                npi))
        .isZero();
  }

  @Then("provider {string} still has effective date {string} and revalidation due date {string}")
  public void dates(String npi, String effective, String due) throws Exception {
    JsonNode p = provider(npi);
    assertThat(p.path("effectiveDate").asText()).isEqualTo(effective);
    assertThat(p.path("revalidationDueDate").asText()).isEqualTo(due);
  }
}
