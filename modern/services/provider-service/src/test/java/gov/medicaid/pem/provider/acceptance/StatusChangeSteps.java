package gov.medicaid.pem.provider.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.testsupport.PemJwt;
import io.cucumber.java.Before;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
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

/** Steps for MAM-33 (a sanction status change applied through provider-service). */
public class StatusChangeSteps {

  private static final String TERMINATIONS = "/actuator/metrics/pem.provider.terminations";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private MvcResult result;
  private double terminationsBefore;

  @Before("@MAM-33")
  public void captureCounter() throws Exception {
    terminationsBefore = terminations();
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

  private void change(String role, String action, String reason, String effectiveDate, String npi)
      throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("action", action);
    if (reason != null) {
      body.put("reason", reason);
    }
    if (effectiveDate != null) {
      body.put("effectiveDate", effectiveDate);
    }
    MockHttpServletRequestBuilder req =
        post("/api/v1/providers/{npi}/status-changes", npi)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body));
    RequestPostProcessor t = token(role);
    if (t != null) {
      req = req.with(t);
    }
    result = mvc.perform(req).andReturn();
  }

  @When(
      "a {string} caller sends the status change {string} with reason {string} and effective date {string} for provider {string}")
  public void sendsWithReason(
      String role, String action, String reason, String effectiveDate, String npi)
      throws Exception {
    change(role, action, reason, effectiveDate, npi);
  }

  @When(
      "a {string} caller sends the status change {string} with effective date {string} for provider {string}")
  public void sends(String role, String action, String effectiveDate, String npi) throws Exception {
    change(role, action, null, effectiveDate, npi);
  }

  @When(
      "a {string} caller sends the status change {string} without an effective date for provider {string}")
  public void sendsWithoutDate(String role, String action, String npi) throws Exception {
    change(role, action, "LEIE exclusion", null, npi);
  }

  @Then("the status-change answer is {int} with enrollStatus {string}")
  public void answered(int status, String enrollStatus) throws Exception {
    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(status);
    JsonNode p = json.readTree(result.getResponse().getContentAsString());
    assertThat(p.path("enrollStatus").asText()).isEqualTo(enrollStatus);
  }

  @Then("the status-change answer is {int} problem+json")
  public void problem(int status) throws Exception {
    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(status);
    assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
  }

  @Then("the status-change answer is {int}")
  public void status(int status) {
    assertThat(result.getResponse().getStatus()).isEqualTo(status);
  }

  private double terminations() throws Exception {
    MvcResult r = mvc.perform(get(TERMINATIONS).with(PemJwt.admin())).andReturn();
    if (r.getResponse().getStatus() == 404) {
      return 0;
    }
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString())
        .path("measurements")
        .get(0)
        .path("value")
        .asDouble();
  }

  @Then("the terminations counter rose by {int}")
  public void terminationsRose(int by) throws Exception {
    assertThat(terminations()).isEqualTo(terminationsBefore + by);
  }

  @Then("the terminations counter is unchanged")
  public void terminationsUnchanged() throws Exception {
    assertThat(terminations()).isEqualTo(terminationsBefore);
  }

  @Then("GET of provider {string} returns enrollStatus {string}")
  public void getStatus(String npi, String enrollStatus) throws Exception {
    MvcResult r =
        mvc.perform(get("/api/v1/providers/{npi}", npi).with(PemJwt.system())).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(json.readTree(r.getResponse().getContentAsString()).path("enrollStatus").asText())
        .isEqualTo(enrollStatus);
  }

  @Then(
      "the status change of {string} is announced once from {string} to {string} with reason {string} and actor {string}")
  public void announced(String npi, String from, String to, String reason, String actor)
      throws Exception {
    List<JsonNode> events = new ArrayList<>();
    for (String p :
        jdbc.queryForList(
            "SELECT payload::text FROM outbox_event WHERE type = 'ProviderStatusChanged' AND subject = ?",
            String.class,
            npi)) {
      events.add(json.readTree(p));
    }
    assertThat(events).hasSize(1);
    JsonNode e = events.get(0);
    assertThat(e.path("subject").asText()).isEqualTo(npi);
    JsonNode d = e.path("data");
    assertThat(d.path("npi").asText()).isEqualTo(npi);
    assertThat(d.path("from").asText()).isEqualTo(from);
    assertThat(d.path("to").asText()).isEqualTo(to);
    assertThat(d.path("reason").asText()).isEqualTo(reason);
    assertThat(d.path("actor").asText()).isEqualTo(actor);
  }

  @Then("no event of any type is written for {string}")
  public void noEvent(String npi) {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE subject = ?", Integer.class, npi))
        .isZero();
  }
}
