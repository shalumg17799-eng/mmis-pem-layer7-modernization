package gov.medicaid.pem.screening.acceptance;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static gov.medicaid.pem.screening.TestSources.MOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Steps for MAM-28 (a screening on request, and one re-screening per RevalidationDue). */
public class RescreenSteps {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private MvcResult result;
  private String requestedId;

  @Before("@MAM-28")
  public void reset() {
    MOCK.resetAll();
    TestSources.stubToken();
    jdbc.update("DELETE FROM screening_result");
    jdbc.update("DELETE FROM screening");
    jdbc.update("DELETE FROM outbox_event");
  }

  private static RequestPostProcessor token(String role) {
    return switch (role) {
      case "ANALYST" -> PemJwt.analyst();
      case "SUPERVISOR" -> PemJwt.supervisor();
      case "ADMIN" -> PemJwt.admin();
      case "SYSTEM" -> PemJwt.system();
      case "PROVIDER" -> PemJwt.provider("1003000111");
      default -> null;
    };
  }

  @Given("provider-service does not know NPI {string}")
  public void unknown(String npi) {
    MOCK.stubFor(
        get(urlPathEqualTo("/api/v1/providers/" + npi))
            .willReturn(
                aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", "application/problem+json")
                    .withBody("{\"title\":\"Not Found\",\"status\":404}")));
  }

  @Given("the sources' request log is cleared")
  public void clearLog() {
    MOCK.resetRequests();
  }

  private MvcResult request(String role, String npi) throws Exception {
    MockHttpServletRequestBuilder req =
        post("/api/v1/screenings")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"npi\":\"" + npi + "\"}");
    RequestPostProcessor t = token(role);
    return mvc.perform(t == null ? req : req.with(t)).andReturn();
  }

  @When("{string} requests a screening of NPI {string}")
  public void requests(String role, String npi) throws Exception {
    result = request(role, npi);
    if (result.getResponse().getStatus() == 202) {
      requestedId =
          json.readTree(result.getResponse().getContentAsString()).path("screeningId").asText();
    }
  }

  @Then("the request answers 202 with a MANUAL screening of NPI {string} in status {string}")
  public void accepted(String npi, String status) throws Exception {
    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(202);
    JsonNode s = json.readTree(result.getResponse().getContentAsString());
    assertThat(s.path("screeningId").asText()).matches("[0-9a-f-]{36}");
    assertThat(s.path("npi").asText()).isEqualTo(npi);
    assertThat(s.path("reason").asText()).isEqualTo("MANUAL");
    assertThat(s.path("status").asText()).isEqualTo(status);
  }

  @Then("the request answers {int} problem+json")
  public void problem(int status) throws Exception {
    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(status);
    assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
  }

  private JsonNode requested() throws Exception {
    long end = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    JsonNode s = null;
    while (System.nanoTime() < end) {
      MvcResult r =
          mvc.perform(
                  MockMvcRequestBuilders.get("/api/v1/screenings/{id}", requestedId)
                      .with(PemJwt.analyst()))
              .andReturn();
      assertThat(r.getResponse().getStatus()).isEqualTo(200);
      s = json.readTree(r.getResponse().getContentAsString());
      if (s.path("status").asText().equals("COMPLETED")) {
        return s;
      }
      Thread.sleep(250);
    }
    throw new AssertionError("screening " + requestedId + " did not complete: " + s);
  }

  @Then("the requested screening completes with exactly 4 results, one per source")
  public void completes() throws Exception {
    JsonNode s = requested();
    assertThat(s.path("reason").asText()).isEqualTo("MANUAL");
    assertThat(s.path("completedAt").asText()).isNotBlank();
    Set<String> sources = new HashSet<>();
    s.path("results").forEach(r -> sources.add(r.path("source").asText()));
    assertThat(s.path("results")).hasSize(4);
    assertThat(sources).containsExactlyInAnyOrder("LICBOARD", "OIGLEIE", "SAMGOV", "SSADMF");
  }

  @Then("the requested screening's {string} result is {string} with error code {string}")
  public void sourceResult(String source, String outcome, String code) throws Exception {
    for (JsonNode r : requested().path("results")) {
      if (r.path("source").asText().equals(source)) {
        assertThat(r.path("result").asText()).isEqualTo(outcome);
        assertThat(r.path("errorCode").asText()).isEqualTo(code);
        return;
      }
    }
    throw new AssertionError("no " + source + " result");
  }

  @Then("no screening is stored for NPI {string}")
  public void noneStored(String npi) {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM screening WHERE npi = ?", Integer.class, npi))
        .isZero();
  }

  @Then("the licence board was not asked about licence {string}")
  public void licenceNotAsked(String licence) {
    assertThat(
            MOCK.findAll(
                getRequestedFor(urlPathEqualTo("/license-board/licenses"))
                    .withQueryParam("licenseNo", equalTo(licence))))
        .isEmpty();
  }

  // ---- RevalidationDue --------------------------------------------------------------------------

  private void publish(String npi, String envelope) throws Exception {
    Map<String, Object> cfg =
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, PemContainers.KAFKA.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    try (var producer = new KafkaProducer<String, String>(cfg)) {
      producer.send(new ProducerRecord<>("pem.revalidation.events", npi, envelope)).get();
    }
  }

  private String envelope(UUID id, String type, String npi, Map<String, Object> data)
      throws Exception {
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("id", id.toString());
    e.put("type", type);
    e.put("source", "revalidation-service");
    e.put("time", "2026-11-19T10:00:00Z");
    e.put("subject", npi);
    e.put("correlationId", UUID.randomUUID().toString());
    e.put("data", data);
    return json.writeValueAsString(e);
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

  @When("RevalidationDue for NPI {string} due {string} is delivered twice with the same event id")
  public void revalidationDueTwice(String npi, String due) throws Exception {
    UUID id = UUID.randomUUID();
    String e =
        envelope(
            id,
            "RevalidationDue",
            npi,
            Map.of("caseId", UUID.randomUUID().toString(), "npi", npi, "dueDate", due));
    publish(npi, e);
    awaitProcessed(id);
    publish(npi, e);
    UUID marker = UUID.randomUUID();
    publish(npi, envelope(marker, "TestMarker", npi, Map.of()));
    awaitProcessed(marker); // same topic and key: the redelivery was handled before the marker
  }

  @Then(
      "exactly {int} screening with reason {string} exists for NPI {string} and it is COMPLETED")
  public void screeningsWithReason(int count, String reason, String npi) {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT status FROM screening WHERE npi = ? AND reason = ?", npi, reason);
    assertThat(rows).hasSize(count);
    assertThat(rows).allSatisfy(r -> assertThat(r.get("status")).isEqualTo("COMPLETED"));
  }

  @Then("the ScreeningCompleted event of NPI {string} has reason {string}")
  public void eventReason(String npi, String reason) throws Exception {
    String payload =
        jdbc.queryForObject(
            "SELECT payload::text FROM outbox_event WHERE type = 'ScreeningCompleted' AND subject = ?",
            String.class,
            npi);
    assertThat(json.readTree(payload).path("data").path("reason").asText()).isEqualTo(reason);
  }

  // ---- roles ------------------------------------------------------------------------------------

  @Then(
      "requesting a screening of NPI {string} answers {int} for {string}, {string} and {string}")
  public void roles3(String npi, int status, String a, String b, String c) throws Exception {
    for (String role : List.of(a, b, c)) {
      assertThat(request(role, npi).getResponse().getStatus()).as(role).isEqualTo(status);
    }
  }

  @Then("requesting a screening of NPI {string} answers {int} for {string} and {string}")
  public void roles2(String npi, int status, String a, String b) throws Exception {
    for (String role : List.of(a, b)) {
      assertThat(request(role, npi).getResponse().getStatus()).as(role).isEqualTo(status);
    }
  }

  @Then("requesting a screening of NPI {string} answers 401 without a token")
  public void anonymous(String npi) throws Exception {
    assertThat(request("NONE", npi).getResponse().getStatus()).isEqualTo(401);
  }
}
