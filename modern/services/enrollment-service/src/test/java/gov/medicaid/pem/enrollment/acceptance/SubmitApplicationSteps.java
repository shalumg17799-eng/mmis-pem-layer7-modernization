package gov.medicaid.pem.enrollment.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.testsupport.EventProbe;
import gov.medicaid.pem.testsupport.OpenApiContract;
import gov.medicaid.pem.testsupport.PemContainers;
import gov.medicaid.pem.testsupport.PemJwt;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Steps for MAM-46 (submit an enrolment application and validate it, E101 to E104). */
public class SubmitApplicationSteps {

  private static final Set<String> ORGANISATION_TYPES = Set.of("GR", "GRP", "FA", "HOSP");
  private static final String TOPIC = "pem.enrollment.events";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private MvcResult result;
  private RequestPostProcessor lastToken;

  @Given("no applications exist")
  public void noApplications() {
    jdbc.update("DELETE FROM application_error");
    jdbc.update("DELETE FROM application");
    jdbc.update("DELETE FROM outbox_event");
  }

  @Given("the provider-type mapping table holds IND to IN, GRP to GR and HOSP to FA")
  public void mapping() {
    for (String[] m : new String[][] {{"IND", "IN"}, {"GRP", "GR"}, {"HOSP", "FA"}}) {
      jdbc.update(
          "INSERT INTO provider_type_mapping (feed_code, legacy_code, description) VALUES (?, ?, 'MAM-46 test')"
              + " ON CONFLICT (feed_code) DO NOTHING",
          m[0],
          m[1]);
    }
  }

  /**
   * An application as the portal sends it; individual RIVERA MARIA or organisation SAMPLE CLINIC 01
   * by type.
   */
  private Map<String, Object> application(String npi, String type, String taxonomy) {
    Map<String, Object> a = new LinkedHashMap<>();
    a.put("npi", npi);
    a.put("medicaidId", "M00000" + (npi.length() >= 3 ? npi.substring(npi.length() - 3) : "000"));
    a.put("providerType", type);
    a.put("taxonomy", taxonomy);
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
    return a;
  }

  private static RequestPostProcessor providerToken(String npi) {
    return PemJwt.provider(npi.matches("[0-9]{10}") ? npi : "1003000111");
  }

  private void submit(String body, RequestPostProcessor token) throws Exception {
    MockHttpServletRequestBuilder req =
        post("/api/v1/applications").contentType(MediaType.APPLICATION_JSON).content(body);
    if (token != null) {
      req = req.with(token);
    }
    lastToken = token;
    result = mvc.perform(req).andReturn();
  }

  @When(
      "a PROVIDER submits an application for NPI {string} with type {string} and taxonomy {string}")
  public void providerSubmits(String npi, String type, String taxonomy) throws Exception {
    submit(json.writeValueAsString(application(npi, type, taxonomy)), providerToken(npi));
  }

  @When("a {string} submits a valid application for NPI {string}")
  public void roleSubmits(String role, String npi) throws Exception {
    RequestPostProcessor token =
        switch (role) {
          case "PROVIDER" -> PemJwt.provider(npi);
          case "ANALYST" -> PemJwt.analyst();
          case "SUPERVISOR" -> PemJwt.supervisor();
          case "ADMIN" -> PemJwt.admin();
          case "SYSTEM" -> PemJwt.system();
          default -> throw new IllegalArgumentException(role);
        };
    submit(json.writeValueAsString(application(npi, "IN", "207Q00000X")), token);
  }

  @When("a valid application for NPI {string} is submitted without a token")
  public void anonymous(String npi) throws Exception {
    submit(json.writeValueAsString(application(npi, "IN", "207Q00000X")), null);
  }

  @When("a PROVIDER submits the raw body {string}")
  public void rawBody(String body) throws Exception {
    submit(body, PemJwt.provider("1003000555"));
  }

  private JsonNode body() throws Exception {
    return json.readTree(result.getResponse().getContentAsString());
  }

  @Then("the answer is {int}")
  public void status(int status) throws Exception {
    assertThat(result.getResponse().getStatus())
        .as("answer %s", result.getResponse().getContentAsString())
        .isEqualTo(status);
  }

  @Then("the answer is problem+json unless it is 201")
  public void problemJson() {
    if (result.getResponse().getStatus() != 201) {
      assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
    }
  }

  @Then(
      "the application has status {string}, source {string}, provider type {string} and no errors")
  public void applicationShows(String status, String source, String type) throws Exception {
    JsonNode a = body();
    assertThat(a.path("applicationId").asText()).isNotBlank();
    assertThat(a.path("status").asText()).isEqualTo(status);
    assertThat(a.path("source").asText()).isEqualTo(source);
    assertThat(a.path("providerType").asText()).isEqualTo(type);
    assertThat(a.path("errors").isMissingNode() || a.path("errors").isEmpty()).isTrue();
  }

  @Then("the answer matches the enrollment-service contract for submitApplication")
  public void contract() throws Exception {
    OpenApiContract.of("enrollment-service")
        .assertResponse(
            "submitApplication",
            result.getResponse().getStatus(),
            result.getResponse().getContentAsString());
  }

  private static List<String> rows(DataTable table) {
    return table.asMaps().stream().map(r -> r.get("code") + "/" + r.get("field")).toList();
  }

  private static List<String> errors(JsonNode array) {
    List<String> out = new ArrayList<>();
    array.forEach(e -> out.add(e.path("code").asText() + "/" + e.path("field").asText()));
    return out;
  }

  @Then("the problem lists exactly these errors:")
  public void problemErrors(DataTable table) throws Exception {
    assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(errors(body().path("errors"))).containsExactlyInAnyOrderElementsOf(rows(table));
  }

  @Then("the last application is stored as {string} with exactly these errors:")
  public void stored(String status, DataTable table) {
    Map<String, Object> last =
        jdbc.queryForMap("SELECT id, status FROM application ORDER BY id DESC LIMIT 1");
    assertThat(last.get("status")).isEqualTo(status);
    List<String> stored =
        jdbc.queryForList(
            "SELECT trim(code) || '/' || field FROM application_error WHERE application_id = ?",
            String.class,
            last.get("id"));
    assertThat(stored).containsExactlyInAnyOrderElementsOf(rows(table));
  }

  @Then("the same provider sees it under the REJECTED applications")
  public void listedForSameUser() throws Exception {
    MvcResult list =
        mvc.perform(get("/api/v1/applications").param("status", "REJECTED").with(lastToken))
            .andReturn();
    assertThat(list.getResponse().getStatus()).isEqualTo(200);
    JsonNode items = json.readTree(list.getResponse().getContentAsString()).path("items");
    assertThat(items).hasSize(1);
    assertThat(items.get(0).path("status").asText()).isEqualTo("REJECTED");
    assertThat(errors(items.get(0).path("errors"))).containsExactly("E101/npi");
  }

  @Then("{int} application(s) is/are stored for NPI {string}")
  public void storedForNpi(int count, String npi) {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM application WHERE npi = ?", Integer.class, npi))
        .isEqualTo(count);
  }

  @Then("{int} application(s) is/are stored")
  public void storedTotal(int count) {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM application", Integer.class))
        .isEqualTo(count);
  }

  @Then("the first application for NPI {string} is still {string}")
  public void firstStill(String npi, String status) {
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM application WHERE npi = ? ORDER BY id LIMIT 1",
                String.class,
                npi))
        .isEqualTo(status);
  }

  @Then("{int} {string} event(s) is/are published for NPI {string}")
  public void published(int count, String type, String npi) {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE type = ? AND subject = ?",
                Integer.class,
                type,
                npi))
        .as("%s events for %s", type, npi)
        .isEqualTo(count);
  }

  @Then("no event is published")
  public void noEvent() {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event", Integer.class)).isZero();
  }

  private JsonNode event(String type, String npi) throws Exception {
    String payload =
        jdbc.queryForObject(
            "SELECT payload::text FROM outbox_event WHERE type = ? AND subject = ? ORDER BY created_at DESC LIMIT 1",
            String.class,
            type,
            npi);
    JsonNode envelope = json.readTree(payload);
    assertThat(envelope.path("type").asText()).isEqualTo(type);
    assertThat(envelope.path("source").asText()).isEqualTo("enrollment-service");
    assertThat(envelope.path("subject").asText()).isEqualTo(npi);
    return envelope.path("data");
  }

  @Then(
      "the ApplicationValidated event for NPI {string} carries Medicaid ID {string}, type {string}, taxonomy {string} and the individual RIVERA MARIA MD12345 TX")
  public void validatedEvent(String npi, String medicaidId, String type, String taxonomy)
      throws Exception {
    JsonNode data = event("ApplicationValidated", npi);
    assertThat(data.path("applicationId").asText())
        .isEqualTo(body().path("applicationId").asText());
    assertThat(data.path("npi").asText()).isEqualTo(npi);
    assertThat(data.path("medicaidId").asText()).isEqualTo(medicaidId);
    assertThat(data.path("providerType").asText()).isEqualTo(type);
    assertThat(data.path("taxonomy").asText()).isEqualTo(taxonomy);
    JsonNode ind = data.path("individual");
    assertThat(ind.path("lastName").asText()).isEqualTo("RIVERA");
    assertThat(ind.path("firstName").asText()).isEqualTo("MARIA");
    assertThat(ind.path("licenseNo").asText()).isEqualTo("MD12345");
    assertThat(ind.path("licenseState").asText()).isEqualTo("TX");
  }

  @Then("the ApplicationValidated event for NPI {string} has provider type {string}")
  public void validatedType(String npi, String type) throws Exception {
    assertThat(event("ApplicationValidated", npi).path("providerType").asText()).isEqualTo(type);
  }

  @Then("the ApplicationRejected event for NPI {string} carries exactly these errors:")
  public void rejectedEvent(String npi, DataTable table) throws Exception {
    JsonNode data = event("ApplicationRejected", npi);
    assertThat(data.path("applicationId").asText()).isNotBlank();
    assertThat(errors(data.path("errors"))).containsExactlyInAnyOrderElementsOf(rows(table));
  }

  @Then("the ApplicationValidated event of the application reaches Kafka with key {string}")
  public void onKafka(String npi) throws Exception {
    String id = body().path("applicationId").asText();
    JsonNode e =
        EventProbe.await(
            PemContainers.KAFKA.getBootstrapServers(),
            TOPIC,
            n ->
                n.path("type").asText().equals("ApplicationValidated")
                    && n.path("data").path("applicationId").asText().equals(id),
            Duration.ofSeconds(30));
    assertThat(e.path("subject").asText()).isEqualTo(npi);
  }
}
