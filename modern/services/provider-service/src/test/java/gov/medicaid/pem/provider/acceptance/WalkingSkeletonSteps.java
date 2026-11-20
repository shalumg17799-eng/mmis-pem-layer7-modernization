package gov.medicaid.pem.provider.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.testsupport.OpenApiContract;
import gov.medicaid.pem.testsupport.PemJwt;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Steps for MAM-34 (walking skeleton: provider lookup by NPI). */
public class WalkingSkeletonSteps {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private MvcResult result;

  @Given(
      "a seeded active individual provider with NPI {string} named {string} with risk tier {string}")
  public void seeded(String npi, String name, String tier) {
    String[] n = name.split(" ");
    jdbc.update("DELETE FROM provider WHERE npi = ?", npi);
    jdbc.update(
        "INSERT INTO provider (public_id, npi, medicaid_id, provider_type, taxonomy, risk_tier, enroll_status, screen_flag,"
            + " effective_date, reval_due_date, last_name, first_name, license_no, license_state)"
            + " VALUES (?, ?, '900000101', 'IN', '207Q00000X', ?, 'A', 'P', DATE '2026-01-15', DATE '2031-01-15', ?, ?,"
            + " 'MD90101', 'TX')",
        UUID.randomUUID(),
        npi,
        tier,
        n[1],
        n[0]);
  }

  @When("an {string} looks up NPI {string}")
  public void staffLooksUp(String role, String npi) throws Exception {
    result =
        mvc.perform(
                get("/api/v1/providers/{npi}", npi)
                    .with(PemJwt.user(role.toLowerCase() + "1", null, role)))
            .andReturn();
  }

  @When("NPI {string} is looked up without a token")
  public void anonymous(String npi) throws Exception {
    result = mvc.perform(get("/api/v1/providers/{npi}", npi)).andReturn();
  }

  @When("a provider whose NPI is {string} looks up NPI {string}")
  public void providerLooksUp(String ownNpi, String npi) throws Exception {
    result =
        mvc.perform(get("/api/v1/providers/{npi}", npi).with(PemJwt.provider(ownNpi))).andReturn();
  }

  @Then("the answer is {int}")
  public void status(int status) {
    assertThat(result.getResponse().getStatus()).isEqualTo(status);
  }

  @Then(
      "the provider has NPI {string}, status {string}, screen flag {string} and risk tier {string}")
  public void provider(String npi, String status, String flag, String tier) throws Exception {
    JsonNode p = body();
    assertThat(p.path("npi").asText()).isEqualTo(npi);
    assertThat(p.path("enrollStatus").asText()).isEqualTo(status);
    assertThat(p.path("screenFlag").asText()).isEqualTo(flag);
    assertThat(p.path("riskTier").asText()).isEqualTo(tier);
  }

  @Then("the provider's individual name is {string} {string}")
  public void name(String first, String last) throws Exception {
    JsonNode i = body().path("individual");
    assertThat(i.path("firstName").asText()).isEqualTo(first);
    assertThat(i.path("lastName").asText()).isEqualTo(last);
  }

  @Then("the answer matches the provider-service contract for {word}")
  public void contract(String operationId) throws Exception {
    OpenApiContract.of("provider-service")
        .assertResponse(
            operationId,
            result.getResponse().getStatus(),
            result.getResponse().getContentAsString());
  }

  private JsonNode body() throws Exception {
    return json.readTree(result.getResponse().getContentAsString());
  }
}
