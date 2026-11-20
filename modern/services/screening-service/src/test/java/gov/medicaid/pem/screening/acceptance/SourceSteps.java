package gov.medicaid.pem.screening.acceptance;

import static gov.medicaid.pem.screening.TestSources.MOCK;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import gov.medicaid.pem.screening.Bounded;
import gov.medicaid.pem.screening.ScreeningEvents;
import gov.medicaid.pem.screening.ScreeningWorld;
import gov.medicaid.pem.testsupport.PemJwt;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Steps of MAM-20 (sources that fail, are slow or do not know a licence; a provider that cannot be
 * read) and MAM-22 (the owner of an organisation against the LEIE). Several steps are also used by
 * the features of MAM-28 and MAM-32.
 */
public class SourceSteps {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private UUID registration;
  private StubMapping providerDown;

  // ---- the mocked world -------------------------------------------------------------------

  @Given("the {string} source answers HTTP {int} for {string}")
  public void sourceAnswers(String source, int status, String key) {
    ScreeningWorld.sourceError(source, key, status);
  }

  @Given("the {string} source answers only after {int} milliseconds for {string}")
  public void sourceSlow(String source, int millis, String key) {
    ScreeningWorld.sourceSlow(source, key, millis);
  }

  @Given(
      "provider-service holds the organisation {string} of type {string} with NPI {string} and owner NPI {string}")
  public void organisation(String name, String type, String npi, String owner) {
    ScreeningWorld.holds(ScreeningWorld.organization(npi, type, name, owner));
  }

  @Given(
      "provider-service holds the organisation {string} of type {string} with NPI {string} and no owner NPI")
  public void organisationWithoutOwner(String name, String type, String npi) {
    ScreeningWorld.holds(ScreeningWorld.organization(npi, type, name, null));
  }

  @Given(
      "provider-service holds the organisation {string} of type {string} with NPI {string} and the owner NPI member {string}")
  public void organisationWithOwnerMember(String name, String type, String npi, String member) {
    ObjectNode p = ScreeningWorld.organization(npi, type, name, null);
    ObjectNode organization = (ObjectNode) p.get("organization");
    switch (member) {
      case "blank" -> organization.put("ownerNpi", "   ");
      case "empty" -> organization.put("ownerNpi", "");
      case "null" -> organization.putNull("ownerNpi");
      default -> throw new IllegalArgumentException(member);
    }
    ScreeningWorld.holds(p);
  }

  @Given("provider-service answers HTTP {int} for NPI {string}")
  public void providerAnswers(int status, String npi) {
    providerDown = ScreeningWorld.providerAnswers(npi, status);
  }

  @When(
      "provider-service is back with the individual {string} {string} with NPI {string} and licence {string} {string}")
  public void providerIsBack(String first, String last, String npi, String licence, String state) {
    MOCK.removeStubMapping(providerDown);
    ScreeningWorld.holds(ScreeningWorld.individual(npi, first, last, licence, state));
    ScreeningWorld.licence(licence, state, "ACTIVE", "2029-01", 3);
  }

  @When(
      "provider-service publishes ProviderRegistered for NPI {string} and does not wait for it")
  public void publishWithoutWaiting(String npi) {
    registration = ScreeningEvents.providerRegistered(npi);
  }

  // ---- what was asked ---------------------------------------------------------------------

  @Then("the request log of the {string} source shows {int} lookup(s) for {string}")
  public void lookupsFor(String source, int count, String key) {
    assertThat(ScreeningWorld.lookups(source, key))
        .as("lookups of %s for %s", source, key)
        .isEqualTo(count);
  }

  @Then("the request log of the {string} source shows {int} lookup(s) in all")
  public void lookupsInAll(String source, int count) {
    assertThat(ScreeningWorld.lookups(source)).as("lookups of %s", source).isEqualTo(count);
  }

  @Then("the licence board request log shows no lookup")
  public void noLicenceLookup() {
    assertThat(ScreeningWorld.lookups("LICBOARD")).isZero();
  }

  @Then("no source was asked about anything")
  public void noSourceAsked() {
    assertThat(ScreeningWorld.allLookups()).isZero();
  }

  @Then("provider-service has been asked about NPI {string} at least {int} times")
  public void providerAsked(String npi, int times) {
    Bounded.until(
        "provider-service is asked about " + npi + " at least " + times + " times",
        () -> ScreeningWorld.providerReads(npi) >= times);
  }

  // ---- what was stored --------------------------------------------------------------------

  @Then("no screening is stored for NPI {string}")
  public void noScreening(String npi) {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM screening WHERE npi = ?", Integer.class, npi))
        .isZero();
  }

  @Then("no ScreeningCompleted event is written for NPI {string}")
  public void noEvent(String npi) {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE type = 'ScreeningCompleted' AND subject = ?",
                Integer.class,
                npi))
        .isZero();
  }

  @Then("the registration of NPI {string} is processed by screening-service")
  public void registrationProcessed(String npi) {
    Bounded.until(
        "the registration of " + npi + " is processed",
        () ->
            jdbc.queryForObject(
                    "SELECT count(*) FROM processed_event WHERE event_id = ?",
                    Integer.class,
                    registration)
                > 0);
  }

  @Then("exactly {int} COMPLETED screening(s) exist(s) for NPI {string}")
  public void completedScreenings(int count, String npi) {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM screening WHERE npi = ? AND status = 'COMPLETED'",
                Integer.class,
                npi))
        .isEqualTo(count);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM screening WHERE npi = ?", Integer.class, npi))
        .isEqualTo(count);
  }

  // ---- what the API shows -----------------------------------------------------------------

  private JsonNode onlyScreening(String npi) throws Exception {
    MvcResult r =
        mvc.perform(
                MockMvcRequestBuilders.get("/api/v1/screenings?npi=" + npi).with(PemJwt.analyst()))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    JsonNode items = json.readTree(r.getResponse().getContentAsString()).path("items");
    assertThat(items).as("screenings of " + npi).hasSize(1);
    return items.get(0);
  }

  private static JsonNode result(JsonNode screening, String source) {
    for (JsonNode r : screening.path("results")) {
      if (source.equals(r.path("source").asText())) {
        return r;
      }
    }
    throw new AssertionError("no " + source + " result in " + screening);
  }

  @Then(
      "the {string} result for NPI {string} is {string} with no error code and a detail containing {string}")
  public void resultWithoutCode(String source, String npi, String expected, String text)
      throws Exception {
    JsonNode r = result(onlyScreening(npi), source);
    assertThat(r.path("result").asText()).isEqualTo(expected);
    assertThat(r.path("errorCode").isMissingNode() || r.path("errorCode").isNull())
        .as("error code of %s: %s", source, r.path("errorCode"))
        .isTrue();
    assertThat(r.path("detail").asText()).contains(text);
  }

  @Then("the screening of NPI {string} took less than {int} seconds")
  public void tookLessThan(String npi, int seconds) throws Exception {
    JsonNode s = onlyScreening(npi);
    Duration took =
        Duration.between(
            Instant.parse(s.path("startedAt").asText()),
            Instant.parse(s.path("completedAt").asText()));
    assertThat(took).isLessThan(Duration.ofSeconds(seconds));
  }

  @Then(
      "the single OIGLEIE result for NPI {string} is {string} with error code {string} and a detail naming {string} and {string}")
  public void singleLeie(String npi, String expected, String code, String first, String second)
      throws Exception {
    JsonNode s = onlyScreening(npi);
    Map<String, Integer> perSource = new LinkedHashMap<>();
    s.path("results").forEach(r -> perSource.merge(r.path("source").asText(), 1, Integer::sum));
    assertThat(perSource.get("OIGLEIE")).as("OIGLEIE results of the screening").isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM screening_result r JOIN screening s ON s.id = r.screening_id"
                    + " WHERE s.npi = ? AND r.source = 'OIGLEIE'",
                Integer.class,
                npi))
        .isEqualTo(1);
    JsonNode leie = result(s, "OIGLEIE");
    assertThat(leie.path("result").asText()).isEqualTo(expected);
    assertThat(leie.path("errorCode").asText()).isEqualTo(code);
    assertThat(leie.path("detail").asText()).contains(first).contains(second);
  }
}
