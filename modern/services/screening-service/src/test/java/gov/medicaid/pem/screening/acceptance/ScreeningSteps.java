package gov.medicaid.pem.screening.acceptance;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static gov.medicaid.pem.screening.TestSources.MOCK;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.screening.TestSources;
import gov.medicaid.pem.testsupport.PemContainers;
import gov.medicaid.pem.testsupport.PemJwt;
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
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Steps for MAM-18 (screen against the mocked sources) and MAM-25 (verdict, results,
 * ScreeningCompleted, read API).
 */
public class ScreeningSteps {

  private static final List<String> SOURCES = List.of("LICBOARD", "OIGLEIE", "SAMGOV", "SSADMF");

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private final Map<String, Map<String, Object>> providers = new LinkedHashMap<>();
  private JsonNode remembered;
  private UUID storedScreening;

  @Before("@MAM-18 or @MAM-25")
  public void reset() {
    MOCK.resetAll();
    TestSources.stubToken();
    jdbc.update("DELETE FROM screening_result");
    jdbc.update("DELETE FROM screening");
    jdbc.update("DELETE FROM outbox_event");
  }

  // ---- the mocked world
  // -------------------------------------------------------------------------------------------

  @Given("the sources answer clean by default and the licence board knows no licence")
  public void cleanDefaults() {
    MOCK.stubFor(
        get(urlPathEqualTo("/leie/exclusions"))
            .atPriority(10)
            .willReturn(okJson("{\"source\":\"OIGLEIE\",\"excluded\":false}")));
    MOCK.stubFor(
        get(urlPathEqualTo("/sam/debarments"))
            .atPriority(10)
            .willReturn(okJson("{\"source\":\"SAMGOV\",\"debarred\":false}")));
    MOCK.stubFor(
        get(urlPathEqualTo("/ssa-dmf/deaths"))
            .atPriority(10)
            .willReturn(okJson("{\"source\":\"SSADMF\",\"deceased\":false}")));
    MOCK.stubFor(
        get(urlPathEqualTo("/license-board/licenses"))
            .atPriority(10)
            .willReturn(okJson("{\"source\":\"LICBOARD\",\"found\":false}")));
  }

  @Given(
      "provider-service holds the individual {string} {string} with NPI {string}, taxonomy {string} and licence {string} {string}")
  public void providerHolds(
      String first, String last, String npi, String taxonomy, String licence, String state)
      throws Exception {
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("npi", npi);
    p.put("medicaidId", "MCD000" + npi.substring(7));
    p.put("providerType", "IN");
    p.put("taxonomy", taxonomy);
    p.put("enrollStatus", "P");
    p.put("screenFlag", "U");
    p.put("effectiveDate", "2026-11-18");
    p.put("version", 0);
    p.put(
        "individual",
        Map.of("lastName", last, "firstName", first, "licenseNo", licence, "licenseState", state));
    providers.put(npi, p);
    MOCK.stubFor(
        get(urlPathEqualTo("/api/v1/providers/" + npi))
            .willReturn(okJson(json.writeValueAsString(p))));
  }

  @Given("the licence board answers {string} until {string} for licence {string} {string}")
  public void licence(String status, String expiry, String licence, String state) {
    MOCK.stubFor(
        get(urlPathEqualTo("/license-board/licenses"))
            .atPriority(1)
            .withQueryParam("licenseNo", equalTo(licence))
            .withQueryParam("state", equalTo(state))
            .willReturn(
                okJson(
                    "{\"source\":\"LICBOARD\",\"licenseNo\":\""
                        + licence
                        + "\",\"state\":\""
                        + state
                        + "\",\"found\":true,\"status\":\""
                        + status
                        + "\",\"expiry\":\""
                        + expiry
                        + "\"}")));
  }

  @Given("the LEIE lists NPI {string} with exclusion type {string} since {string}")
  public void leie(String npi, String type, String since) {
    MOCK.stubFor(
        get(urlPathEqualTo("/leie/exclusions"))
            .atPriority(1)
            .withQueryParam("npi", equalTo(npi))
            .willReturn(
                okJson(
                    "{\"source\":\"OIGLEIE\",\"npi\":\""
                        + npi
                        + "\",\"excluded\":true,\"exclusionType\":\""
                        + type
                        + "\",\"description\":\"PROGRAM-RELATED FRAUD\",\"exclusionDate\":\""
                        + since
                        + "\"}")));
  }

  @Given("SAM lists the name {string} as debarred")
  public void sam(String name) {
    MOCK.stubFor(
        get(urlPathEqualTo("/sam/debarments"))
            .atPriority(1)
            .withQueryParam("name", equalTo(name))
            .willReturn(
                okJson(
                    "{\"source\":\"SAMGOV\",\"debarred\":true,\"entityName\":\""
                        + name
                        + "\",\"debarredFrom\":\"2025-06-01\"}")));
  }

  @Given("the SSA DMF lists NPI {string} as deceased on {string}")
  public void dmf(String npi, String date) {
    MOCK.stubFor(
        get(urlPathEqualTo("/ssa-dmf/deaths"))
            .atPriority(1)
            .withQueryParam("npi", equalTo(npi))
            .willReturn(
                okJson(
                    "{\"source\":\"SSADMF\",\"npi\":\""
                        + npi
                        + "\",\"deceased\":true,\"dateOfDeath\":\""
                        + date
                        + "\"}")));
  }

  @Given(
      "only the source {string} fails for NPI {string} named {string} with licence {string} {string}")
  public void onlyFails(String source, String npi, String name, String licence, String state) {
    switch (source) {
      case "LICBOARD" -> licence("EXPIRED", "2024-01", licence, state);
      case "OIGLEIE" -> leie(npi, "1128(a)(1)", "2023-01-15");
      case "SAMGOV" -> sam(name);
      case "SSADMF" -> dmf(npi, "2025-08-01");
      default -> throw new IllegalArgumentException(source);
    }
  }

  @Given(
      "a completed FAIL screening of NPI {string} whose OIGLEIE result is {string} with error code {string}")
  public void storedFail(String npi, String result, String code) {
    storedScreening = UUID.randomUUID();
    Long id =
        jdbc.queryForObject(
            "INSERT INTO screening (public_id, npi, reason, status, result, started_at, completed_at)"
                + " VALUES (?, ?, 'INITIAL', 'COMPLETED', 'FAIL', now() - interval '1 minute', now()) RETURNING id",
            Long.class,
            storedScreening,
            npi);
    for (String s : SOURCES) {
      boolean leie = s.equals("OIGLEIE");
      jdbc.update(
          "INSERT INTO screening_result (screening_id, source, result, verdict_at, detail, error_code) VALUES (?, ?, ?, now(), ?, ?)",
          id,
          s,
          leie ? result : "P",
          leie ? "OWNER 1990000111 EXCLUDED" : "CLEAN",
          leie ? code : null);
    }
  }

  // ---- events
  // ------------------------------------------------------------------------------------------------------

  private void send(UUID id, String type, String npi) throws Exception {
    Map<String, Object> p =
        providers.getOrDefault(
            npi, Map.of("npi", npi, "taxonomy", "207Q00000X", "providerType", "IN"));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("npi", npi);
    data.put("medicaidId", p.getOrDefault("medicaidId", "MCD000000"));
    data.put("providerType", p.get("providerType"));
    data.put("taxonomy", p.get("taxonomy"));
    data.put("enrollStatus", "P");
    data.put("screenFlag", "U");
    data.put("displayName", "MARIA RIVERA");
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("id", id.toString());
    e.put("type", type);
    e.put("source", "provider-service");
    e.put("time", "2026-11-18T10:00:00Z");
    e.put("subject", npi);
    e.put("correlationId", UUID.randomUUID().toString());
    e.put("data", data);
    Map<String, Object> cfg =
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, PemContainers.KAFKA.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    try (var producer = new KafkaProducer<String, String>(cfg)) {
      producer
          .send(new ProducerRecord<>("pem.provider.events", npi, json.writeValueAsString(e)))
          .get();
    }
  }

  private void awaitProcessed(UUID id) throws Exception {
    long end = System.nanoTime() + Duration.ofSeconds(60).toNanos();
    while (System.nanoTime() < end) {
      Integer n =
          jdbc.queryForObject(
              "SELECT count(*) FROM processed_event WHERE event_id = ?", Integer.class, id);
      if (n != null && n > 0) {
        return;
      }
      Thread.sleep(200);
    }
    throw new AssertionError("event " + id + " was not processed by screening-service within 60 s");
  }

  @When(
      "provider-service publishes ProviderRegistered for NPI {string} and screening-service has processed it")
  @When(
      "provider-service publishes ProviderRegistered for NPI {string} again and screening-service has processed it")
  public void registered(String npi) throws Exception {
    UUID id = UUID.randomUUID();
    send(id, "ProviderRegistered", npi);
    awaitProcessed(id);
  }

  @When("ProviderRegistered for NPI {string} is delivered twice with the same event id")
  public void twice(String npi) throws Exception {
    UUID id = UUID.randomUUID();
    send(id, "ProviderRegistered", npi);
    awaitProcessed(id);
    send(id, "ProviderRegistered", npi);
    UUID marker = UUID.randomUUID();
    send(marker, "ProviderStatusChanged", npi); // same key: handled after the redelivery
    awaitProcessed(marker);
  }

  // ---- reading
  // -------------------------------------------------------------------------------------------------------

  private JsonNode list(String query) throws Exception {
    MvcResult r =
        mvc.perform(
                MockMvcRequestBuilders.get("/api/v1/screenings?" + query).with(PemJwt.analyst()))
            .andReturn();
    assertThat(r.getResponse().getStatus())
        .as("list %s: %s", query, r.getResponse().getContentAsString())
        .isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString());
  }

  private JsonNode only(String npi) throws Exception {
    JsonNode items = list("npi=" + npi).path("items");
    assertThat(items).hasSize(1);
    return items.get(0);
  }

  private JsonNode byId(String npi) throws Exception {
    String id = only(npi).path("screeningId").asText();
    MvcResult r =
        mvc.perform(
                MockMvcRequestBuilders.get("/api/v1/screenings/{id}", id).with(PemJwt.analyst()))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString());
  }

  private static Map<String, JsonNode> bySource(JsonNode screening) {
    Map<String, JsonNode> out = new LinkedHashMap<>();
    screening.path("results").forEach(r -> out.put(r.path("source").asText(), r));
    return out;
  }

  @Then(
      "the analyst sees {int} screening(s) for NPI {string} with reason {string} and one result for each of LICBOARD, OIGLEIE, SAMGOV and SSADMF")
  public void sees(int count, String npi, String reason) throws Exception {
    JsonNode items = list("npi=" + npi).path("items");
    assertThat(items).hasSize(count);
    for (JsonNode s : items) {
      assertThat(s.path("reason").asText()).isEqualTo(reason);
      assertThat(s.path("npi").asText()).isEqualTo(npi);
      List<String> sources = new ArrayList<>();
      s.path("results").forEach(r -> sources.add(r.path("source").asText()));
      assertThat(sources).containsExactlyInAnyOrderElementsOf(SOURCES);
    }
  }

  @Then("the licence board was asked about licence {string} in {string}")
  public void licenceAsked(String licence, String state) {
    assertThat(
            MOCK.findAll(
                getRequestedFor(urlPathEqualTo("/license-board/licenses"))
                    .withQueryParam("licenseNo", equalTo(licence))
                    .withQueryParam("state", equalTo(state))))
        .hasSize(1);
  }

  @Then(
      "the results for NPI {string} are LICBOARD {string}, OIGLEIE {string}, SAMGOV {string} and SSADMF {string}, each with a verdict time and a detail")
  public void results(String npi, String lic, String leie, String sam, String dmf)
      throws Exception {
    Map<String, JsonNode> r = bySource(only(npi));
    assertThat(r.get("LICBOARD").path("result").asText()).isEqualTo(lic);
    assertThat(r.get("OIGLEIE").path("result").asText()).isEqualTo(leie);
    assertThat(r.get("SAMGOV").path("result").asText()).isEqualTo(sam);
    assertThat(r.get("SSADMF").path("result").asText()).isEqualTo(dmf);
    for (JsonNode x : r.values()) {
      assertThat(x.path("verdictAt").asText()).isNotBlank();
      assertThat(x.path("detail").asText()).isNotBlank();
    }
  }

  @Then("each of the four sources was asked exactly once about NPI {string} with licence {string}")
  public void askedOnce(String npi, String licence) {
    assertThat(
            MOCK.findAll(
                getRequestedFor(urlPathEqualTo("/license-board/licenses"))
                    .withQueryParam("licenseNo", equalTo(licence))))
        .hasSize(1);
    assertThat(
            MOCK.findAll(
                getRequestedFor(urlPathEqualTo("/leie/exclusions"))
                    .withQueryParam("npi", equalTo(npi))))
        .hasSize(1);
    assertThat(
            MOCK.findAll(
                getRequestedFor(urlPathEqualTo("/sam/debarments"))
                    .withQueryParam("npi", equalTo(npi))))
        .hasSize(1);
    assertThat(
            MOCK.findAll(
                getRequestedFor(urlPathEqualTo("/ssa-dmf/deaths"))
                    .withQueryParam("npi", equalTo(npi))))
        .hasSize(1);
  }

  @Then(
      "the {string} result for NPI {string} is {string} with error code {string} and a detail containing {string}")
  public void failed(String source, String npi, String result, String code, String text)
      throws Exception {
    JsonNode r = bySource(only(npi)).get(source);
    assertThat(r.path("result").asText()).isEqualTo(result);
    assertThat(r.path("errorCode").asText()).isEqualTo(code);
    assertThat(r.path("detail").asText()).contains(text);
    assertThat(r.path("detail").asText().length()).isLessThanOrEqualTo(60);
  }

  @Then(
      "reading the screening of NPI {string} by id gives status {string}, result {string} and a completion time")
  public void completed(String npi, String status, String result) throws Exception {
    JsonNode s = byId(npi);
    assertThat(s.path("status").asText()).isEqualTo(status);
    assertThat(s.path("result").asText()).isEqualTo(result);
    assertThat(s.path("completedAt").asText()).isNotBlank();
  }

  @Then("the {string} result for NPI {string} is {string} and the other three are {string}")
  public void oneFails(String source, String npi, String result, String others) throws Exception {
    bySource(only(npi))
        .forEach(
            (s, r) ->
                assertThat(r.path("result").asText())
                    .as(s)
                    .isEqualTo(s.equals(source) ? result : others));
  }

  @Then(
      "the screening of NPI {string} read by id holds exactly 4 results, one per source, each with a result, a verdict time and a detail")
  public void four(String npi) throws Exception {
    JsonNode s = byId(npi);
    assertThat(s.path("results")).hasSize(4);
    assertThat(bySource(s).keySet()).containsExactlyInAnyOrderElementsOf(SOURCES);
    s.path("results")
        .forEach(
            r -> {
              assertThat(r.path("result").asText()).isIn("P", "F", "C");
              assertThat(r.path("verdictAt").asText()).isNotBlank();
              assertThat(r.path("detail").asText()).isNotBlank();
            });
  }

  @Then("only the {string} result of NPI {string} carries an error code, {string}")
  public void onlyCode(String source, String npi, String code) throws Exception {
    bySource(byId(npi))
        .forEach(
            (s, r) -> {
              if (s.equals(source)) {
                assertThat(r.path("errorCode").asText()).isEqualTo(code);
              } else {
                assertThat(r.path("errorCode").isMissingNode() || r.path("errorCode").isNull())
                    .as(s)
                    .isTrue();
              }
            });
  }

  @When("the first screening of NPI {string} is remembered")
  public void remember(String npi) throws Exception {
    remembered = only(npi);
  }

  @Then("the analyst sees {int} screenings for NPI {string}, each with its own 4 results")
  public void two(int count, String npi) throws Exception {
    JsonNode items = list("npi=" + npi).path("items");
    assertThat(items).hasSize(count);
    items.forEach(s -> assertThat(s.path("results")).hasSize(4));
    assertThat(items.get(0).path("screeningId").asText())
        .isNotEqualTo(items.get(1).path("screeningId").asText());
  }

  @Then("the remembered screening of NPI {string} is unchanged")
  public void unchanged(String npi) throws Exception {
    JsonNode again = null;
    for (JsonNode s : list("npi=" + npi).path("items")) {
      if (s.path("screeningId").asText().equals(remembered.path("screeningId").asText())) {
        again = s;
      }
    }
    assertThat(again).isEqualTo(remembered);
  }

  @Then("{int} ScreeningCompleted event(s) is/are written for NPI {string}")
  public void written(int count, String npi) {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE type = 'ScreeningCompleted' AND subject = ?",
                Integer.class,
                npi))
        .isEqualTo(count);
  }

  @Then(
      "a ScreeningCompleted event with key {string} reaches topic {string} with reason {string}, result {string}, taxonomy {string} and the four sources with result and detail")
  public void onTopic(String key, String topic, String reason, String result, String taxonomy)
      throws Exception {
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
    String expectedId = only(key).path("screeningId").asText();
    JsonNode found = null;
    try (var consumer = new KafkaConsumer<String, String>(cfg)) {
      consumer.subscribe(List.of(topic));
      long end = System.nanoTime() + Duration.ofSeconds(30).toNanos();
      while (System.nanoTime() < end && found == null) {
        for (var rec : consumer.poll(Duration.ofMillis(250))) {
          JsonNode n = json.readTree(rec.value());
          if (key.equals(rec.key())
              && n.path("data").path("screeningId").asText().equals(expectedId)) {
            found = n;
          }
        }
      }
    }
    assertThat(found).as("ScreeningCompleted for %s on %s", key, topic).isNotNull();
    assertThat(found.path("type").asText()).isEqualTo("ScreeningCompleted");
    JsonNode d = found.path("data");
    assertThat(d.path("npi").asText()).isEqualTo(key);
    assertThat(d.path("reason").asText()).isEqualTo(reason);
    assertThat(d.path("result").asText()).isEqualTo(result);
    assertThat(d.path("taxonomy").asText()).isEqualTo(taxonomy);
    List<String> sources = new ArrayList<>();
    d.path("sources")
        .forEach(
            s -> {
              sources.add(s.path("source").asText());
              assertThat(s.path("result").asText()).isNotBlank();
              assertThat(s.path("detail").asText()).isNotBlank();
            });
    assertThat(sources).containsExactlyInAnyOrderElementsOf(SOURCES);
  }

  @When("an ANALYST lists the screenings with result {string}")
  public void listFailed(String result) {
    // the request is made in the Then step, which checks the page
  }

  @Then(
      "the page lists the screening of NPI {string} with OIGLEIE {string} and error code {string}, and has items, page, size and total")
  public void failedPage(String npi, String result, String code) throws Exception {
    JsonNode page = list("result=FAIL");
    assertThat(page.has("page") && page.has("size") && page.has("total")).isTrue();
    JsonNode s = null;
    for (JsonNode x : page.path("items")) {
      if (x.path("npi").asText().equals(npi)) {
        s = x;
      }
    }
    assertThat(s).isNotNull();
    assertThat(s.path("result").asText()).isEqualTo("FAIL");
    JsonNode leie = bySource(s).get("OIGLEIE");
    assertThat(leie.path("result").asText()).isEqualTo(result);
    assertThat(leie.path("errorCode").asText()).isEqualTo(code);
  }

  @Then("reading that screening by id answers 200 and an unknown id answers 404 with problem+json")
  public void readOne() throws Exception {
    assertThat(
            mvc.perform(
                    MockMvcRequestBuilders.get("/api/v1/screenings/{id}", storedScreening)
                        .with(PemJwt.analyst()))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(200);
    MvcResult unknown =
        mvc.perform(
                MockMvcRequestBuilders.get("/api/v1/screenings/{id}", UUID.randomUUID())
                    .with(PemJwt.analyst()))
            .andReturn();
    assertThat(unknown.getResponse().getStatus()).isEqualTo(404);
    assertThat(unknown.getResponse().getContentType()).startsWith("application/problem+json");
  }

  @Then("{string} gets {int} from the screening list and from reading that screening")
  public void roles(String role, int status) throws Exception {
    RequestPostProcessor token =
        switch (role) {
          case "ANALYST" -> PemJwt.analyst();
          case "SUPERVISOR" -> PemJwt.supervisor();
          case "ADMIN" -> PemJwt.admin();
          case "PROVIDER" -> PemJwt.provider("1003000444");
          case "SYSTEM" -> PemJwt.system();
          default -> null;
        };
    for (String path : List.of("/api/v1/screenings", "/api/v1/screenings/" + storedScreening)) {
      MockHttpServletRequestBuilder req = MockMvcRequestBuilders.get(path);
      if (token != null) {
        req = req.with(token);
      }
      assertThat(mvc.perform(req).andReturn().getResponse().getStatus())
          .as("%s %s", role, path)
          .isEqualTo(status);
    }
  }
}
