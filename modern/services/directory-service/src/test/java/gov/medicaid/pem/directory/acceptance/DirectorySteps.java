package gov.medicaid.pem.directory.acceptance;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static gov.medicaid.pem.directory.TestSources.MOCK;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.directory.TestSources;
import gov.medicaid.pem.testsupport.PemContainers;
import io.cucumber.java.Before;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Steps for MAM-61 (the directory of ACTIVE providers, fed by provider and risk events). */
public class DirectorySteps {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private String lastTopic;
  private String lastKey;
  private String lastEnvelope;
  private long rememberedTotal;

  @Before("@MAM-61")
  public void reset() {
    MOCK.resetAll();
    TestSources.stubToken();
    jdbc.update("DELETE FROM directory_entry");
  }

  // ---- provider-service as WireMock
  // -------------------------------------------------------------------------------

  private void holds(String npi, Map<String, Object> provider) throws Exception {
    MOCK.stubFor(
        get(urlPathEqualTo("/api/v1/providers/" + npi))
            .willReturn(okJson(json.writeValueAsString(provider))));
  }

  private static Map<String, Object> base(
      String npi, String medicaidId, String type, String taxonomy) {
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("npi", npi);
    p.put("medicaidId", medicaidId);
    p.put("providerType", type);
    p.put("taxonomy", taxonomy);
    p.put("enrollStatus", "A");
    p.put("screenFlag", "P");
    p.put("effectiveDate", "2026-11-18");
    p.put("version", 3);
    return p;
  }

  @Given(
      "provider-service holds the individual {string} {string} with NPI {string}, Medicaid ID {string}, taxonomy {string} and licence {string} {string}")
  public void individual(
      String first,
      String last,
      String npi,
      String medicaidId,
      String taxonomy,
      String licence,
      String state)
      throws Exception {
    Map<String, Object> p = base(npi, medicaidId, "IN", taxonomy);
    p.put(
        "individual",
        Map.of("lastName", last, "firstName", first, "licenseNo", licence, "licenseState", state));
    holds(npi, p);
  }

  private void organisation(
      String type, String legal, String dba, String npi, String medicaidId, String taxonomy)
      throws Exception {
    Map<String, Object> p = base(npi, medicaidId, type, taxonomy);
    p.put("organization", Map.of("legalName", legal, "dbaName", dba));
    holds(npi, p);
  }

  @Given(
      "provider-service holds the facility {string} doing business as {string} with NPI {string}, Medicaid ID {string} and taxonomy {string}")
  public void facility(String legal, String dba, String npi, String medicaidId, String taxonomy)
      throws Exception {
    organisation("FA", legal, dba, npi, medicaidId, taxonomy);
  }

  @Given(
      "provider-service holds the group {string} doing business as {string} with NPI {string}, Medicaid ID {string} and taxonomy {string}")
  public void group(String legal, String dba, String npi, String medicaidId, String taxonomy)
      throws Exception {
    organisation("GR", legal, dba, npi, medicaidId, taxonomy);
  }

  @When("provider-service becomes unreachable")
  public void unreachable() {
    MOCK.resetAll();
    MOCK.stubFor(get(urlPathEqualTo("/api/v1/providers/1003000111")).willReturn(serverError()));
  }

  // ---- events
  // -------------------------------------------------------------------------------------------------------

  private void send(String topic, String type, String source, String npi, Map<String, Object> data)
      throws Exception {
    UUID id = UUID.randomUUID();
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("id", id.toString());
    e.put("type", type);
    e.put("source", source);
    e.put("time", "2026-11-18T10:00:00Z");
    e.put("subject", npi);
    e.put("correlationId", UUID.randomUUID().toString());
    e.put("data", data);
    lastTopic = topic;
    lastKey = npi;
    lastEnvelope = json.writeValueAsString(e);
    publish(topic, npi, lastEnvelope);
    awaitProcessed(id);
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
    throw new AssertionError("event " + id + " was not processed by directory-service within 45 s");
  }

  @When(
      "directory-service consumes ProviderRegistered for NPI {string} with Medicaid ID {string}, type {string}, taxonomy {string} and name {string}")
  public void registered(String npi, String medicaidId, String type, String taxonomy, String name)
      throws Exception {
    Map<String, Object> d = new LinkedHashMap<>();
    d.put("npi", npi);
    d.put("medicaidId", medicaidId);
    d.put("providerType", type);
    d.put("taxonomy", taxonomy);
    d.put("enrollStatus", "P");
    d.put("screenFlag", "U");
    d.put("displayName", name);
    send("pem.provider.events", "ProviderRegistered", "provider-service", npi, d);
  }

  @When("directory-service consumes RiskTierAssigned for NPI {string} with tier {string}")
  public void tier(String npi, String tier) throws Exception {
    send(
        "pem.risk.events",
        "RiskTierAssigned",
        "risk-service",
        npi,
        Map.of("npi", npi, "tier", tier, "ruleSetVersion", 1));
  }

  @When(
      "directory-service consumes ProviderStatusChanged for NPI {string} from {string} to {string}")
  public void statusChanged(String npi, String from, String to) throws Exception {
    statusChangedDue(npi, from, to, null);
  }

  @When(
      "directory-service consumes ProviderStatusChanged for NPI {string} from {string} to {string} with revalidation due date {string}")
  public void statusChangedDue(String npi, String from, String to, String due) throws Exception {
    Map<String, Object> d = new LinkedHashMap<>();
    d.put("npi", npi);
    d.put("from", from);
    d.put("to", to);
    d.put("actor", "analyst1");
    d.put("revalidationDueDate", due);
    send("pem.provider.events", "ProviderStatusChanged", "provider-service", npi, d);
  }

  @When("the same event is delivered to directory-service again")
  public void redeliver() throws Exception {
    publish(lastTopic, lastKey, lastEnvelope);
    UUID marker = UUID.randomUUID();
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("id", marker.toString());
    e.put("type", "TestMarker");
    e.put("source", "test");
    e.put("time", "2026-11-18T10:00:00Z");
    e.put("subject", lastKey);
    e.put("correlationId", UUID.randomUUID().toString());
    e.put("data", Map.of());
    publish(lastTopic, lastKey, json.writeValueAsString(e));
    awaitProcessed(marker); // same topic and key: the redelivery was handled before the marker
  }

  // ---- the public directory
  // -----------------------------------------------------------------------------------------

  private JsonNode page() throws Exception {
    MvcResult r =
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/directory/providers").param("size", "200"))
            .andReturn();
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString());
  }

  private List<JsonNode> entries(String npi) throws Exception {
    List<JsonNode> out = new ArrayList<>();
    page()
        .path("items")
        .forEach(
            i -> {
              if (i.path("npi").asText().equals(npi)) {
                out.add(i);
              }
            });
    return out;
  }

  @Then("the public directory does not list NPI {string}")
  public void notListed(String npi) throws Exception {
    assertThat(entries(npi)).isEmpty();
    JsonNode p = page();
    assertThat(p.path("total").asInt()).isEqualTo(p.path("items").size());
  }

  @Then("the public directory lists NPI {string} once")
  public void listedOnce(String npi) throws Exception {
    assertThat(entries(npi)).hasSize(1);
  }

  @Then(
      "the public directory lists NPI {string} once with Medicaid ID {string}, type {string}, risk tier {string}, taxonomy {string} and name {string}")
  public void listedWith(
      String npi, String medicaidId, String type, String tier, String taxonomy, String name)
      throws Exception {
    List<JsonNode> e = entries(npi);
    assertThat(e).hasSize(1);
    JsonNode x = e.get(0);
    assertThat(x.path("medicaidId").asText()).isEqualTo(medicaidId);
    assertThat(x.path("providerType").asText()).isEqualTo(type);
    assertThat(x.path("riskTier").asText()).isEqualTo(tier);
    assertThat(x.path("taxonomy").asText()).isEqualTo(taxonomy);
    assertThat(x.path("displayName").asText()).isEqualTo(name);
  }

  @Then(
      "the public directory lists NPI {string} once with Medicaid ID {string}, type {string}, no risk tier, taxonomy {string} and name {string}")
  public void listedNoTier(String npi, String medicaidId, String type, String taxonomy, String name)
      throws Exception {
    List<JsonNode> e = entries(npi);
    assertThat(e).hasSize(1);
    JsonNode x = e.get(0);
    assertThat(x.path("medicaidId").asText()).isEqualTo(medicaidId);
    assertThat(x.path("providerType").asText()).isEqualTo(type);
    assertThat(x.path("riskTier").isMissingNode() || x.path("riskTier").isNull()).isTrue();
    assertThat(x.path("taxonomy").asText()).isEqualTo(taxonomy);
    assertThat(x.path("displayName").asText()).isEqualTo(name);
  }

  @Then(
      "the public directory shows name {string} for NPI {string} without {string}, padding or more than 60 characters")
  public void name(String name, String npi, String forbidden) throws Exception {
    String shown = entries(npi).get(0).path("displayName").asText();
    assertThat(shown).isEqualTo(name);
    assertThat(shown).doesNotContain(forbidden);
    assertThat(shown).isEqualTo(shown.strip()).doesNotContain("  ");
    assertThat(shown.length()).isLessThanOrEqualTo(60);
  }

  @Then("provider-service was asked for NPI {string} with the SYSTEM token")
  public void askedWithToken(String npi) {
    assertThat(
            MOCK.findAll(
                getRequestedFor(urlPathEqualTo("/api/v1/providers/" + npi))
                    .withHeader("Authorization", equalTo("Bearer " + TestSources.SYSTEM_TOKEN))))
        .isNotEmpty();
  }

  @When("the directory total is remembered")
  public void remember() throws Exception {
    rememberedTotal = page().path("total").asLong();
  }

  @Then("the directory total is unchanged")
  public void totalUnchanged() throws Exception {
    assertThat(page().path("total").asLong()).isEqualTo(rememberedTotal);
  }
}
