package gov.medicaid.pem.directory.acceptance;

import static gov.medicaid.pem.directory.TestSources.MOCK;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.directory.DirectoryFixtures;
import gov.medicaid.pem.directory.DirectoryFixtures.Row;
import gov.medicaid.pem.directory.Fhir4Shape;
import gov.medicaid.pem.directory.TestSources;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Steps for MAM-65 (FHIR R4 Practitioner, Organization and PractitionerRole, read-only, public).
 * The provider-service and Kafka steps of MAM-61 (DirectorySteps) are reused for the reinstatement.
 */
public class FhirSteps {

  static final String NPI_SYSTEM = "http://hl7.org/fhir/sid/us-npi";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private MvcResult last;
  private final List<MvcResult> answers = new ArrayList<>();
  private final List<Integer> writeStatuses = new ArrayList<>();
  private JsonNode remembered;

  @Before("@MAM-65")
  public void reset() {
    MOCK.resetAll();
    TestSources.stubToken();
    DirectoryFixtures.clear(jdbc);
  }

  // ---- data -----------------------------------------------------------------------------------

  @Given("the FHIR directory holds:")
  public void holds(DataTable table) {
    DirectoryFixtures.clear(jdbc);
    for (Map<String, String> r : table.asMaps()) {
      String type = r.get("type");
      String first = r.get("firstName");
      String last = r.get("lastName");
      String legal = r.get("legalName");
      String display = "IN".equals(type) ? (first + " " + last).trim() : legal;
      DirectoryFixtures.insert(
          jdbc,
          new Row(
              r.get("npi"),
              r.get("medicaidId"),
              type,
              r.get("tier"),
              r.get("status"),
              display,
              r.get("taxonomy"),
              last,
              first,
              legal,
              r.get("licenseNo"),
              r.get("licenseState")));
    }
  }

  // ---- calls ----------------------------------------------------------------------------------

  private MvcResult search(String resource, String query) throws Exception {
    MockHttpServletRequestBuilder get = MockMvcRequestBuilders.get("/fhir/" + resource);
    if (!query.isEmpty()) {
      for (String pair : query.split("&")) {
        int eq = pair.indexOf('=');
        get.param(pair.substring(0, eq), pair.substring(eq + 1));
      }
    }
    return mvc.perform(get).andReturn(); // no token: the API is public
  }

  @When("anyone searches FHIR {string} with {string}")
  public void anyoneSearches(String resource, String query) throws Exception {
    last = search(resource, query);
    answers.add(last);
  }

  @When("a caller sends POST, PUT and DELETE to {string}")
  public void writes(String path) throws Exception {
    String body = "{\"resourceType\":\"Practitioner\",\"id\":\"1003000999\",\"active\":true}";
    writeStatuses.clear();
    for (MockHttpServletRequestBuilder b :
        List.of(
            MockMvcRequestBuilders.post(path),
            MockMvcRequestBuilders.put(path),
            MockMvcRequestBuilders.delete(path))) {
      writeStatuses.add(
          mvc.perform(b.contentType("application/fhir+json").content(body))
              .andReturn()
              .getResponse()
              .getStatus());
    }
  }

  // ---- answers --------------------------------------------------------------------------------

  private JsonNode bundle() throws Exception {
    assertThat(last.getResponse().getStatus())
        .as(last.getResponse().getContentAsString())
        .isEqualTo(200);
    return json.readTree(last.getResponse().getContentAsString());
  }

  private JsonNode resource(String type, String npi) throws Exception {
    for (JsonNode e : bundle().path("entry")) {
      JsonNode r = e.path("resource");
      if (type.equals(r.path("resourceType").asText()) && npi.equals(r.path("id").asText())) {
        return r;
      }
    }
    throw new AssertionError("no " + type + " " + npi + " in " + bundle());
  }

  @Then("the FHIR answer is {int} with content type {string}")
  public void status(int status, String contentType) throws Exception {
    assertThat(last.getResponse().getStatus())
        .as(last.getResponse().getContentAsString())
        .isEqualTo(status);
    assertThat(last.getResponse().getContentType()).startsWith(contentType);
  }

  @Then("the FHIR Bundle is a searchset with total {int}")
  public void searchset(int total) throws Exception {
    JsonNode b = bundle();
    assertThat(b.path("resourceType").asText()).isEqualTo("Bundle");
    assertThat(b.path("type").asText()).isEqualTo("searchset");
    assertThat(b.path("total").asInt()).isEqualTo(total);
    assertThat(b.path("entry").size()).isEqualTo(total);
  }

  @Then("the FHIR Bundle entries are {string} resources for the NPIs {string}")
  public void entries(String type, String npis) throws Exception {
    List<String> expected = npis.isEmpty() ? List.of() : Arrays.asList(npis.split(","));
    List<String> found = new ArrayList<>();
    for (JsonNode e : bundle().path("entry")) {
      JsonNode r = e.path("resource");
      assertThat(r.path("resourceType").asText()).isEqualTo(type);
      found.add(r.path("id").asText());
    }
    assertThat(found).containsExactlyElementsOf(expected);
  }

  @Then("the FHIR Practitioner {string} carries the NPI identifier {string}")
  public void npiIdentifier(String npi, String value) throws Exception {
    JsonNode ids = resource("Practitioner", npi).path("identifier");
    boolean found = false;
    for (JsonNode id : ids) {
      found |=
          NPI_SYSTEM.equals(id.path("system").asText()) && value.equals(id.path("value").asText());
    }
    assertThat(found).as("NPI identifier %s in %s", value, ids).isTrue();
  }

  @Then("the FHIR {word} {string} has the identifiers NPI {string} and Medicaid ID {string}")
  public void identifiers(String type, String npi, String npiValue, String medicaidId)
      throws Exception {
    JsonNode ids = resource(type, npi).path("identifier");
    assertThat(ids).hasSize(2);
    assertThat(ids.get(0).path("system").asText()).isEqualTo(NPI_SYSTEM);
    assertThat(ids.get(0).path("value").asText()).isEqualTo(npiValue);
    assertThat(ids.get(1).path("type").path("text").asText()).isEqualTo("Medicaid ID");
    assertThat(ids.get(1).path("value").asText()).isEqualTo(medicaidId);
  }

  @Then("the FHIR Practitioner {string} has the name family {string} and given {string}")
  public void name(String npi, String family, String given) throws Exception {
    JsonNode name = resource("Practitioner", npi).path("name").path(0);
    assertThat(name.path("family").asText()).isEqualTo(family);
    assertThat(name.path("given")).hasSize(1);
    assertThat(name.path("given").get(0).asText()).isEqualTo(given);
  }

  @Then("the FHIR Practitioner {string} has the qualification {string} issued by {string}")
  public void qualification(String npi, String licence, String state) throws Exception {
    JsonNode q = resource("Practitioner", npi).path("qualification").path(0);
    assertThat(q.path("identifier").path(0).path("value").asText()).isEqualTo(licence);
    assertThat(q.path("issuer").path("display").asText()).isEqualTo(state);
  }

  @Then("the FHIR Practitioner {string} holds no address, telecom or network element")
  public void noContactData(String npi) throws Exception {
    String text = resource("Practitioner", npi).toString().toLowerCase();
    assertThat(text).doesNotContain("\"address\"", "\"telecom\"", "network");
  }

  @Then("the FHIR Organization {string} is named {string} with the legacy type code {string}")
  public void organization(String npi, String name, String typeCode) throws Exception {
    JsonNode o = resource("Organization", npi);
    assertThat(o.path("name").asText()).isEqualTo(name);
    assertThat(o.path("type").path(0).path("coding").path(0).path("code").asText())
        .isEqualTo(typeCode);
  }

  @Then(
      "the FHIR PractitionerRole {string} references the practitioner {string} with the specialty code {string}")
  public void role(String npi, String practitionerNpi, String taxonomy) throws Exception {
    JsonNode r = resource("PractitionerRole", npi);
    assertThat(r.path("practitioner").path("reference").asText())
        .isEqualTo("Practitioner/" + practitionerNpi);
    assertThat(r.path("specialty").path(0).path("coding").path(0).path("code").asText())
        .isEqualTo(taxonomy);
  }

  // ---- read-only ------------------------------------------------------------------------------

  @Then("each of the three calls was rejected with a 4xx status and never a 2xx")
  public void rejected() {
    assertThat(writeStatuses).hasSize(3);
    assertThat(writeStatuses).allSatisfy(s -> assertThat(s).isBetween(400, 499));
  }

  private JsonNode practitionerByNpi(String npi) throws Exception {
    MvcResult r = search("Practitioner", "identifier=" + NPI_SYSTEM + "|" + npi);
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString());
  }

  @Given("the FHIR Practitioner for the NPI {string} is remembered")
  public void remember(String npi) throws Exception {
    remembered = practitionerByNpi(npi);
    assertThat(remembered.path("total").asInt()).isEqualTo(1);
  }

  @Then("the FHIR Practitioner for the NPI {string} is unchanged")
  public void unchanged(String npi) throws Exception {
    assertThat(practitionerByNpi(npi)).isEqualTo(remembered);
  }

  // ---- AC-7 -----------------------------------------------------------------------------------

  @Then("every FHIR Bundle answered so far has no structural error against FHIR R4")
  public void validStructure() throws Exception {
    assertThat(answers).isNotEmpty();
    for (MvcResult r : answers) {
      assertThat(r.getResponse().getStatus()).isEqualTo(200);
      JsonNode b = json.readTree(r.getResponse().getContentAsString());
      assertThat(Fhir4Shape.violations(b)).as(b.toString()).isEmpty();
    }
  }

  @Then("the FHIR Bundles answered so far hold {int} resources in total")
  public void resourceCount(int expected) throws Exception {
    int n = 0;
    for (MvcResult r : answers) {
      n += json.readTree(r.getResponse().getContentAsString()).path("entry").size();
    }
    assertThat(n).isEqualTo(expected);
  }
}
