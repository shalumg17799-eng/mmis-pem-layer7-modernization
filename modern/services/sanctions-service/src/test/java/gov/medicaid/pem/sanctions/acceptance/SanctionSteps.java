package gov.medicaid.pem.sanctions.acceptance;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import gov.medicaid.pem.sanctions.TestProviderService;
import gov.medicaid.pem.testsupport.EventProbe;
import gov.medicaid.pem.testsupport.PemContainers;
import gov.medicaid.pem.testsupport.PemJwt;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Steps for MAM-52 (record a sanction, apply it through provider-service, keep its history). */
public class SanctionSteps {

  private static final HttpClient HTTP = HttpClient.newHttpClient();

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private MvcResult result;
  private String historyBefore;

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

  // ---- given -----------------------------------------------------------------------------------

  @Given("the sanctions history and outbox are empty")
  public void empty() {
    jdbc.update("DELETE FROM sanction");
    jdbc.update("DELETE FROM outbox_event");
    TestProviderService.reset();
  }

  @Given("provider-service holds provider {string} with status {string}")
  public void holds(String npi, String status) {
    TestProviderService.holds(npi, status);
  }

  @Given("provider-service has no provider {string}")
  public void unknown(String npi) {
    TestProviderService.unknown(npi);
  }

  @Given("provider-service fails with {string} for {string}")
  public void fails(String fault, String npi) {
    TestProviderService.fails(npi, fault);
  }

  @Given("the sanction history of {string} is read as {string}")
  public void historyRead(String npi, String role) throws Exception {
    MvcResult r = history(npi, role);
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    historyBefore = r.getResponse().getContentAsString();
  }

  @Given("{string} recorded the sanctions {string} and then {string} for NPI {string}")
  public void recordedTwo(String role, String first, String second, String npi) throws Exception {
    record(role, first, "SYNTHETIC TEST REASON", "2026-03-31", npi);
    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(201);
    record(role, second, "SYNTHETIC TEST REASON", "2026-04-30", npi);
    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(201);
  }

  // ---- when ------------------------------------------------------------------------------------

  private void record(String role, String action, String reason, String effectiveDate, String npi)
      throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("npi", npi);
    body.put("action", action);
    body.put("reason", reason);
    body.put("effectiveDate", effectiveDate);
    MockHttpServletRequestBuilder req =
        post("/api/v1/sanctions")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body));
    RequestPostProcessor t = token(role);
    if (t != null) {
      req = req.with(t);
    }
    result = mvc.perform(req).andReturn();
  }

  @When(
      "{string} records the sanction {string} with reason {string} and effective date {string} for NPI {string}")
  public void records(String role, String action, String reason, String effectiveDate, String npi)
      throws Exception {
    record(role, action, reason, effectiveDate, npi);
  }

  // ---- the answer ------------------------------------------------------------------------------

  private JsonNode body() throws Exception {
    return json.readTree(result.getResponse().getContentAsString());
  }

  private void status(int expected) throws Exception {
    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(expected);
  }

  @Then(
      "the sanction answer is 201 with npi {string}, action {string}, resulting status {string}, effective date {string}, reason {string}, recordedBy {string} and a recordedAt")
  public void created(
      String npi,
      String action,
      String resulting,
      String effectiveDate,
      String reason,
      String recordedBy)
      throws Exception {
    status(201);
    JsonNode s = body();
    assertThat(s.path("sanctionId").asText()).matches("[0-9a-f-]{36}");
    assertThat(s.path("npi").asText()).isEqualTo(npi);
    assertThat(s.path("action").asText()).isEqualTo(action);
    assertThat(s.path("resultingStatus").asText()).isEqualTo(resulting);
    assertThat(s.path("effectiveDate").asText()).isEqualTo(effectiveDate);
    assertThat(s.path("reason").asText()).isEqualTo(reason);
    assertThat(s.path("recordedBy").asText()).isEqualTo(recordedBy);
    assertThat(OffsetDateTime.parse(s.path("recordedAt").asText())).isNotNull();
  }

  @Then("the sanction answer is 201 with resulting status {string}")
  public void createdWith(String resulting) throws Exception {
    status(201);
    assertThat(body().path("resultingStatus").asText()).isEqualTo(resulting);
  }

  @Then(
      "the sanction answer is {int} problem+json whose detail names {string}, {string}, {string} and {string} and that has no code")
  public void problemNaming(int code, String field, String a, String b, String c) throws Exception {
    status(code);
    assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
    JsonNode p = body();
    assertThat(p.path("detail").asText()).contains(field, a, b, c);
    assertThat(p.has("code")).isFalse();
  }

  @Then("the sanction answer is {int} problem+json whose detail names {string}")
  public void problemNamingNpi(int code, String text) throws Exception {
    status(code);
    assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(body().path("detail").asText()).contains(text);
  }

  @Then("the sanction answer is {int}")
  public void answer(int code) throws Exception {
    status(code);
  }

  // ---- provider-service (WireMock) -------------------------------------------------------------

  private List<LoggedRequest> statusChanges(String npi) {
    return TestProviderService.MOCK.findAll(
        postRequestedFor(urlPathEqualTo("/api/v1/providers/" + npi + "/status-changes")));
  }

  @Then(
      "provider-service received exactly {int} status change(s) for {string} with action {string}, effective date {string}, the answered sanctionId and the SYSTEM token")
  public void receivedFull(int count, String npi, String action, String effectiveDate)
      throws Exception {
    List<LoggedRequest> calls = statusChanges(npi);
    assertThat(calls).hasSize(count);
    JsonNode sent = json.readTree(calls.get(0).getBodyAsString());
    assertThat(sent.path("action").asText()).isEqualTo(action);
    assertThat(sent.path("effectiveDate").asText()).isEqualTo(effectiveDate);
    assertThat(sent.path("sanctionId").asText()).isEqualTo(body().path("sanctionId").asText());
    assertThat(calls.get(0).getHeader("Authorization"))
        .isEqualTo("Bearer " + TestProviderService.SYSTEM_TOKEN);
  }

  @Then(
      "provider-service received exactly {int} status change(s) for {string} with action {string}")
  public void received(int count, String npi, String action) throws Exception {
    List<LoggedRequest> calls = statusChanges(npi);
    assertThat(calls).hasSize(count);
    assertThat(json.readTree(calls.get(0).getBodyAsString()).path("action").asText())
        .isEqualTo(action);
  }

  @Then("provider-service received no status change for {string}")
  public void receivedNone(String npi) {
    assertThat(statusChanges(npi)).isEmpty();
  }

  @Then("provider-service now shows status {string} for {string}")
  public void shows(String status, String npi) throws Exception {
    HttpResponse<String> r =
        HTTP.send(
            HttpRequest.newBuilder(
                    URI.create(TestProviderService.MOCK.baseUrl() + "/api/v1/providers/" + npi))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(r.statusCode()).isEqualTo(200);
    assertThat(json.readTree(r.body()).path("enrollStatus").asText()).isEqualTo(status);
  }

  // ---- events ----------------------------------------------------------------------------------

  private List<JsonNode> events(String npi) throws Exception {
    List<JsonNode> out = new ArrayList<>();
    for (String p :
        jdbc.queryForList(
            "SELECT payload::text FROM outbox_event WHERE type = 'SanctionApplied' AND subject = ?",
            String.class,
            npi)) {
      out.add(json.readTree(p));
    }
    return out;
  }

  @Then(
      "exactly {int} SanctionApplied event(s) is/are written for {string} with action {string}, effective date {string} and the answered sanctionId")
  public void eventFull(int count, String npi, String action, String effectiveDate)
      throws Exception {
    List<JsonNode> e = events(npi);
    assertThat(e).hasSize(count);
    assertThat(e.get(0).path("subject").asText()).isEqualTo(npi);
    assertThat(e.get(0).path("source").asText()).isEqualTo("sanctions-service");
    JsonNode d = e.get(0).path("data");
    assertThat(d.path("npi").asText()).isEqualTo(npi);
    assertThat(d.path("action").asText()).isEqualTo(action);
    assertThat(d.path("effectiveDate").asText()).isEqualTo(effectiveDate);
    assertThat(d.path("sanctionId").asText()).isEqualTo(body().path("sanctionId").asText());
  }

  @Then("exactly {int} SanctionApplied event(s) is/are written for {string} with action {string}")
  public void event(int count, String npi, String action) throws Exception {
    List<JsonNode> e = events(npi);
    assertThat(e).hasSize(count);
    assertThat(e.get(0).path("data").path("action").asText()).isEqualTo(action);
  }

  @Then("no SanctionApplied event is written for {string}")
  public void noEvent(String npi) throws Exception {
    assertThat(events(npi)).isEmpty();
  }

  @Then("the SanctionApplied event for {string} reaches topic {string}")
  public void onTopic(String npi, String topic) throws Exception {
    String id = events(npi).get(0).path("id").asText();
    JsonNode found =
        EventProbe.await(
            PemContainers.KAFKA.getBootstrapServers(),
            topic,
            n -> n.path("id").asText().equals(id),
            Duration.ofSeconds(30));
    assertThat(found.path("type").asText()).isEqualTo("SanctionApplied");
    assertThat(found.path("subject").asText()).isEqualTo(npi);
  }

  // ---- history ---------------------------------------------------------------------------------

  private MvcResult history(String npi, String role) throws Exception {
    MockHttpServletRequestBuilder req = get("/api/v1/sanctions").param("npi", npi);
    RequestPostProcessor t = token(role);
    if (t != null) {
      req = req.with(t);
    }
    return mvc.perform(req).andReturn();
  }

  @Then("the sanction history of {string} is unchanged")
  public void historyUnchanged(String npi) throws Exception {
    MvcResult r = history(npi, "ANALYST");
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(json.readTree(r.getResponse().getContentAsString()))
        .isEqualTo(json.readTree(historyBefore));
  }

  @Then("no sanction is stored for {string}")
  public void noneStored(String npi) throws Exception {
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM sanction WHERE npi = ?", Integer.class, npi))
        .isZero();
    MvcResult r = history(npi, "ANALYST");
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    JsonNode page = json.readTree(r.getResponse().getContentAsString());
    assertThat(page.path("total").asInt()).isZero();
    assertThat(page.path("items")).isEmpty();
  }

  @Then(
      "reading the sanction history of {string} as {string} gives {int} rows with resulting statuses {string} and {string}, page {int} and size {int}")
  public void historyRows(
      String npi, String role, int rows, String first, String second, int page, int size)
      throws Exception {
    MvcResult r = history(npi, role);
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
    JsonNode p = json.readTree(r.getResponse().getContentAsString());
    assertThat(p.path("page").asInt()).isEqualTo(page);
    assertThat(p.path("size").asInt()).isEqualTo(size);
    assertThat(p.path("total").asInt()).isEqualTo(rows);
    assertThat(p.path("items")).hasSize(rows);
    List<String> statuses = new ArrayList<>();
    for (JsonNode i : p.path("items")) {
      assertThat(i.path("sanctionId").asText()).isNotBlank();
      assertThat(i.path("npi").asText()).isEqualTo(npi);
      assertThat(i.path("action").asText()).isIn("T", "S", "R");
      assertThat(i.path("reason").asText()).isEqualTo("SYNTHETIC TEST REASON");
      assertThat(i.path("effectiveDate").asText()).matches("\\d{4}-\\d{2}-\\d{2}");
      assertThat(i.path("recordedBy").asText()).isEqualTo("supervisor1");
      assertThat(OffsetDateTime.parse(i.path("recordedAt").asText())).isNotNull();
      statuses.add(i.path("resultingStatus").asText());
    }
    assertThat(statuses).containsExactlyInAnyOrder(first, second);
  }

  @Then("reading the sanction history of {string} as {string} answers {int}")
  public void historyStatus(String npi, String role, int code) throws Exception {
    assertThat(history(npi, role).getResponse().getStatus()).isEqualTo(code);
  }
}
