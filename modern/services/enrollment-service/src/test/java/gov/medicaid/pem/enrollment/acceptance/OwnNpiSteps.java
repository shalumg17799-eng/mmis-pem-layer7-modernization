package gov.medicaid.pem.enrollment.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.testsupport.PemJwt;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Steps for Q-095 (an individual provider applies only under the NPI of their own token). */
public class OwnNpiSteps {

  private static final Set<String> ORGANISATION_TYPES = Set.of("GR", "GRP", "FA", "HOSP");

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;

  private MvcResult result;

  private void apply(RequestPostProcessor token, String npi, String type) throws Exception {
    Map<String, Object> a = new LinkedHashMap<>();
    a.put("npi", npi);
    a.put("medicaidId", "M00000" + npi.substring(7));
    a.put("providerType", type);
    a.put("taxonomy", ORGANISATION_TYPES.contains(type) ? "193200000X" : "207Q00000X");
    if (ORGANISATION_TYPES.contains(type)) {
      a.put(
          "organization",
          Map.of("legalName", "SAMPLE CLINIC 01", "dbaName", "SAMPLE CLINIC GROUP 01"));
    } else {
      a.put(
          "individual",
          Map.of(
              "lastName",
              "RIVERA",
              "firstName",
              "MARIA",
              "licenseNo",
              "MD12345",
              "licenseState",
              "TX"));
    }
    result =
        mvc.perform(
                post("/api/v1/applications")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(a))
                    .with(token))
            .andReturn();
  }

  @When("the PROVIDER whose token holds NPI {string} applies for NPI {string} with type {string}")
  public void providerApplies(String own, String npi, String type) throws Exception {
    apply(PemJwt.provider(own), npi, type);
  }

  @When("a PROVIDER whose token has no npi claim applies for NPI {string} with type {string}")
  public void noClaimApplies(String npi, String type) throws Exception {
    apply(PemJwt.user("provider9", null, "PROVIDER"), npi, type);
  }

  @Then("the own-NPI answer is 201 with status {string} and provider type {string}")
  public void accepted(String status, String type) throws Exception {
    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(201);
    JsonNode a = json.readTree(result.getResponse().getContentAsString());
    assertThat(a.path("status").asText()).isEqualTo(status);
    assertThat(a.path("providerType").asText()).isEqualTo(type);
  }

  @Then("the own-NPI answer is {int} problem+json")
  public void problem(int status) throws Exception {
    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(status);
    assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
  }
}
