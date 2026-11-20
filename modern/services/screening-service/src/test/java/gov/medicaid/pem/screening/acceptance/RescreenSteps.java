package gov.medicaid.pem.screening.acceptance;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static gov.medicaid.pem.screening.TestSources.MOCK;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.screening.Bounded;
import gov.medicaid.pem.screening.ScreeningEvents;
import gov.medicaid.pem.screening.ScreeningWorld;
import gov.medicaid.pem.testsupport.PemJwt;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Steps of MAM-28: a screening on request, and one re-screening per RevalidationDue. */
public class RescreenSteps {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private MvcResult result;
  private String requestedId;

  private static RequestPostProcessor token(String role) {
    return switch (role) {
      case "ANALYST" -> PemJwt.analyst();
      case "SUPERVISOR" -> PemJwt.supervisor();
      case "ADMIN" -> PemJwt.admin();
      case "SYSTEM" -> PemJwt.system();
      case "PROVIDER" -> PemJwt.provider("1003000111");
      case "NONE" -> null;
      default -> throw new IllegalArgumentException(role);
    };
  }

  @Given("provider-service does not know NPI {string}")
  public void unknown(String npi) {
    ScreeningWorld.providerUnknown(npi);
  }

  @Given("the sources' request log is cleared")
  public void clearLog() {
    MOCK.resetRequests();
  }

  private MvcResult request(String role, String npi) throws Exception {
    MockHttpServletRequestBuilder req =
        MockMvcRequestBuilders.post("/api/v1/screenings")
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
      // the screening runs in the background: it ends before the next scenario cleans the world
      Bounded.until(
          "the requested screening " + requestedId + " is stored",
          () ->
              jdbc.queryForObject(
                      "SELECT count(*) FROM screening WHERE public_id = ?::uuid",
                      Integer.class,
                      requestedId)
                  > 0);
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
  public void problem(int status) {
    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(status);
    assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
  }

  @Then("the request answers {int}")
  public void status(int status) {
    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(status);
  }

  private JsonNode requested() throws Exception {
    JsonNode[] last = new JsonNode[1];
    Bounded.until(
        "the requested screening " + requestedId + " is COMPLETED",
        () -> {
          MvcResult r =
              mvc.perform(
                      MockMvcRequestBuilders.get("/api/v1/screenings/{id}", requestedId)
                          .with(PemJwt.analyst()))
                  .andReturn();
          if (r.getResponse().getStatus() != 200) {
            return false;
          }
          last[0] = json.readTree(r.getResponse().getContentAsString());
          return last[0].path("status").asText().equals("COMPLETED");
        });
    return last[0];
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

  @Then(
      "the requested screening has the id of the 202 answer, result {string} and a ScreeningCompleted event with reason {string}")
  public void sameIdAndEvent(String expected, String reason) throws Exception {
    JsonNode s = requested();
    JsonNode accepted = json.readTree(result.getResponse().getContentAsString());
    assertThat(s.path("screeningId").asText()).isEqualTo(accepted.path("screeningId").asText());
    assertThat(s.path("result").asText()).isEqualTo(expected);
    JsonNode event =
        json.readTree(
            jdbc.queryForObject(
                "SELECT payload::text FROM outbox_event WHERE type = 'ScreeningCompleted' AND subject = ?",
                String.class,
                s.path("npi").asText()));
    assertThat(event.path("data").path("screeningId").asText()).isEqualTo(requestedId);
    assertThat(event.path("data").path("reason").asText()).isEqualTo(reason);
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

  @Then("the licence board was not asked about licence {string}")
  public void licenceNotAsked(String licence) {
    assertThat(
            MOCK.findAll(
                getRequestedFor(urlPathEqualTo("/license-board/licenses"))
                    .withQueryParam("licenseNo", equalTo(licence))))
        .isEmpty();
  }

  // ---- RevalidationDue --------------------------------------------------------------------

  private void awaitProcessed(UUID id) {
    Bounded.until(
        "event " + id + " is processed by screening-service",
        () ->
            jdbc.queryForObject(
                    "SELECT count(*) FROM processed_event WHERE event_id = ?", Integer.class, id)
                > 0);
  }

  @When("RevalidationDue for NPI {string} due {string} is delivered")
  public void revalidationDue(String npi, String due) {
    awaitProcessed(ScreeningEvents.revalidationDue(npi, due));
  }

  @When("RevalidationDue for NPI {string} due {string} is delivered twice with the same event id")
  public void revalidationDueTwice(String npi, String due) throws Exception {
    UUID id = UUID.randomUUID();
    String corr = UUID.randomUUID().toString();
    ScreeningEvents.revalidationDue(id, npi, due, corr);
    awaitProcessed(id);
    ScreeningEvents.revalidationDue(id, npi, due, corr);
    UUID marker = UUID.randomUUID();
    ScreeningEvents.send(
        "pem.revalidation.events",
        npi,
        ScreeningEvents.envelope(
            marker, "TestMarker", "revalidation-service", npi, corr, Map.of()));
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
}
