package gov.medicaid.pem.risk.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.risk.RiskFixtures;
import gov.medicaid.pem.testsupport.PemJwt;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Steps for MAM-38 (read and replace the risk table) and the table set-up of MAM-41. Black box: the
 * REST API through MockMvc with signed-in users; the data in a real Postgres (Testcontainers). The
 * direct database access is the reset to the seeded table and read-only checks of what was stored.
 * The ScreeningCompleted and RiskTierAssigned steps are those of MAM-35 (RiskSteps).
 */
public class RuleTableSteps {

  private static final String RULES = "/api/v1/risk-rules";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private MvcResult result;
  private int eventsBeforeTheChange;

  @Before("@MAM-38 or @MAM-41")
  public void startFromTheSeededTable() {
    RiskFixtures.resetToLegacyTable(jdbc);
    jdbc.update("DELETE FROM outbox_event");
    jdbc.update("DELETE FROM risk_assessment");
  }

  @After("@MAM-38 or @MAM-41")
  public void restoreTheSeededTable() {
    RiskFixtures.resetToLegacyTable(jdbc);
  }

  static RequestPostProcessor token(String caller) {
    return switch (caller) {
      case "no token" -> null;
      case "PROVIDER" -> PemJwt.provider("1003000111");
      case "ANALYST" -> PemJwt.analyst();
      case "SUPERVISOR" -> PemJwt.supervisor();
      case "ADMIN" -> PemJwt.admin();
      case "SYSTEM" -> PemJwt.system();
      default -> throw new IllegalArgumentException("unknown caller " + caller);
    };
  }

  private static List<String> entries(String list) {
    return list.isBlank() ? List.of() : Arrays.stream(list.split(",")).map(String::trim).toList();
  }

  private static List<String> legacyPlus(String prefix, String tier) {
    List<String> all = new ArrayList<>(RiskFixtures.LEGACY);
    all.add(prefix + "=" + tier);
    return all;
  }

  private void save(String caller, String body) throws Exception {
    eventsBeforeTheChange = jdbc.queryForObject("SELECT count(*) FROM outbox_event", Integer.class);
    MockHttpServletRequestBuilder req =
        put(RULES).contentType(MediaType.APPLICATION_JSON).content(body);
    RequestPostProcessor who = token(caller);
    result = mvc.perform(who == null ? req : req.with(who)).andReturn();
  }

  private JsonNode body() throws Exception {
    return json.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode tableInForce() throws Exception {
    MvcResult r = mvc.perform(get(RULES).with(PemJwt.admin())).andReturn();
    assertThat(r.getResponse().getStatus()).as("GET %s as ADMIN", RULES).isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString());
  }

  // ---- Given -----------------------------------------------------------------------------------

  @Given("the initial risk rule set is in force")
  public void initial() throws Exception {
    JsonNode t = tableInForce();
    assertThat(t.path("version").asInt()).isEqualTo(1);
    assertThat(RiskFixtures.rulesOf(t)).containsExactlyInAnyOrderElementsOf(RiskFixtures.LEGACY);
  }

  @Given("risk rule set version {int} is in force with prefix {string} as {string}")
  public void versionInForce(int version, String prefix, String tier) throws Exception {
    assertThat(version).as("this set-up saves on top of version 1").isEqualTo(2);
    save("ADMIN", RiskFixtures.ruleSet(1, "L", legacyPlus(prefix, tier)));
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(tableInForce().path("version").asInt()).isEqualTo(version);
  }

  // ---- When ------------------------------------------------------------------------------------

  @When("caller {string} reads the risk rules")
  public void reads(String caller) throws Exception {
    MockHttpServletRequestBuilder req = get(RULES);
    RequestPostProcessor who = token(caller);
    result = mvc.perform(who == null ? req : req.with(who)).andReturn();
  }

  @When(
      "caller {string} saves based on version {int} the default tier {string} and the legacy rules plus prefix {string} as {string}")
  public void savesLegacyPlus(
      String caller, int version, String defaultTier, String prefix, String tier) throws Exception {
    save(caller, RiskFixtures.ruleSet(version, defaultTier, legacyPlus(prefix, tier)));
  }

  @When(
      "caller {string} saves based on version {int} the default tier {string} and the rules {string}")
  public void savesRules(String caller, int version, String defaultTier, String rules)
      throws Exception {
    save(caller, RiskFixtures.ruleSet(version, defaultTier, entries(rules)));
  }

  @When(
      "caller {string} saves based on version {int} a rule set with default tier {string} and the rule {string} as {string}")
  public void savesOneRule(
      String caller, int version, String defaultTier, String prefix, String tier) throws Exception {
    // "-" means the default tier is left out of the request
    save(
        caller,
        RiskFixtures.ruleSet(
            version, "-".equals(defaultTier) ? null : defaultTier, List.of(prefix + "=" + tier)));
  }

  // ---- Then ------------------------------------------------------------------------------------

  @Then("the risk rules call returns {int}")
  public void returns(int status) {
    assertThat(result.getResponse().getStatus()).isEqualTo(status);
  }

  @Then("the risk rules call returns {int} with a problem+json body")
  public void returnsProblem(int status) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(status);
    assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(body().path("status").asInt()).isEqualTo(status);
    assertThat(body().path("title").asText()).isNotBlank();
  }

  @Then("the risk rule set has version {int} and default tier {string}")
  public void hasVersionAndDefault(int version, String defaultTier) throws Exception {
    assertThat(body().path("version").asInt()).isEqualTo(version);
    assertThat(body().path("defaultTier").asText()).isEqualTo(defaultTier);
  }

  @Then("the risk rule set lists exactly the rules {string}")
  public void listsExactly(String rules) throws Exception {
    assertThat(RiskFixtures.rulesOf(body())).containsExactlyInAnyOrderElementsOf(entries(rules));
  }

  @Then("the table in force is version {int}")
  public void inForceIs(int version) throws Exception {
    assertThat(tableInForce().path("version").asInt()).isEqualTo(version);
  }

  @Then("the table in force is still version {int} with the rules {string}")
  public void inForceStill(int version, String rules) throws Exception {
    JsonNode t = tableInForce();
    assertThat(t.path("version").asInt()).isEqualTo(version);
    assertThat(RiskFixtures.rulesOf(t)).containsExactlyInAnyOrderElementsOf(entries(rules));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM risk_rule_set", Integer.class))
        .as("no further rule set was stored")
        .isEqualTo(version);
  }

  @Then("no event was written by the change")
  public void noEvent() {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event", Integer.class))
        .isEqualTo(eventsBeforeTheChange);
  }

  @Then("the stored assessments of NPI {string} are {string}")
  public void storedAssessments(String npi, String expected) {
    List<String> stored =
        jdbc.queryForList(
            "SELECT tier || '@' || rule_set_version FROM risk_assessment WHERE npi = ? ORDER BY id",
            String.class,
            npi);
    assertThat(stored).containsExactlyElementsOf(entries(expected));
  }
}
