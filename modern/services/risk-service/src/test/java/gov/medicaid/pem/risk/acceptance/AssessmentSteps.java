package gov.medicaid.pem.risk.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.risk.RiskFixtures;
import gov.medicaid.pem.testsupport.PemJwt;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Steps for MAM-41 (assess a risk tier on request). Black box: POST /api/v1/risk-assessments
 * through MockMvc with signed-in users; the data in a real Postgres (Testcontainers). The table
 * set-up steps are those of MAM-38 (RuleTableSteps).
 */
public class AssessmentSteps {

  private static final String ASSESS = "/api/v1/risk-assessments";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private final List<MvcResult> results = new ArrayList<>();
  private MvcResult result;
  private int outboxBefore;
  private int storedBefore;
  private double countedBefore;

  private void ask(String caller, String body) throws Exception {
    MockHttpServletRequestBuilder req =
        post(ASSESS).contentType(MediaType.APPLICATION_JSON).content(body);
    RequestPostProcessor who = RuleTableSteps.token(caller);
    result = mvc.perform(who == null ? req : req.with(who)).andReturn();
    results.add(result);
  }

  private JsonNode body(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString());
  }

  private double tiersAssigned() throws Exception {
    MvcResult r =
        mvc.perform(get("/actuator/metrics/pem.risk.tiers.assigned").with(PemJwt.admin()))
            .andReturn();
    if (r.getResponse().getStatus() == 404) {
      return 0;
    }
    return json.readTree(r.getResponse().getContentAsString())
        .path("measurements")
        .get(0)
        .path("value")
        .asDouble();
  }

  // ---- Given -----------------------------------------------------------------------------------

  @Given("what is stored, announced and counted is noted")
  public void note() throws Exception {
    outboxBefore = jdbc.queryForObject("SELECT count(*) FROM outbox_event", Integer.class);
    storedBefore = jdbc.queryForObject("SELECT count(*) FROM risk_assessment", Integer.class);
    countedBefore = tiersAssigned();
    results.clear();
  }

  // ---- When ------------------------------------------------------------------------------------

  @When("caller {string} assesses the taxonomy {string}")
  public void assesses(String caller, String taxonomy) throws Exception {
    ask(caller, RiskFixtures.assessmentRequest(taxonomy, null));
  }

  @When("caller {string} assesses the taxonomy {string} for NPI {string}")
  public void assessesForNpi(String caller, String taxonomy, String npi) throws Exception {
    ask(caller, RiskFixtures.assessmentRequest(taxonomy, npi));
  }

  @When("caller {string} asks for an assessment without a taxonomy")
  public void withoutTaxonomy(String caller) throws Exception {
    ask(caller, RiskFixtures.assessmentRequest(null, null));
  }

  // ---- Then ------------------------------------------------------------------------------------

  @Then("the assessment answers {int}")
  public void answers(int status) {
    assertThat(result.getResponse().getStatus()).isEqualTo(status);
  }

  @Then("the assessment answers {int} with a problem+json body")
  public void answersProblem(int status) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(status);
    assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(body(result).path("status").asInt()).isEqualTo(status);
    assertThat(body(result).path("title").asText()).isNotBlank();
  }

  @Then(
      "the assessment answers {int} with taxonomy {string}, tier {string} and rule set version {int}")
  public void answersWith(int status, String taxonomy, String tier, int version) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(status);
    JsonNode a = body(result);
    assertThat(a.path("taxonomy").asText()).isEqualTo(taxonomy);
    assertThat(a.path("tier").asText()).isEqualTo(tier);
    assertThat(a.path("ruleSetVersion").asInt()).isEqualTo(version);
  }

  @Then("every assessment answered {int} with tier {string}")
  public void everyAnswered(int status, String tier) throws Exception {
    assertThat(results).isNotEmpty();
    for (MvcResult r : results) {
      assertThat(r.getResponse().getStatus()).isEqualTo(status);
      assertThat(body(r).path("tier").asText()).isEqualTo(tier);
    }
  }

  @Then("nothing was stored, announced or counted since it was noted")
  public void nothingHappened() throws Exception {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event", Integer.class))
        .as("RiskTierAssigned events written")
        .isEqualTo(outboxBefore);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM risk_assessment", Integer.class))
        .as("assessments stored")
        .isEqualTo(storedBefore);
    assertThat(tiersAssigned()).as("tiers-assigned counter").isEqualTo(countedBefore);
  }
}
