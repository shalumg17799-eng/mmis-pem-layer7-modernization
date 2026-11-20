package gov.medicaid.pem.enrollment.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.testsupport.PemJwt;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Steps for MAM-54 (read application status). */
public class ReadApplicationsSteps {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private static final RequestPostProcessor USER_A = PemJwt.provider("1003000111");
  private static final RequestPostProcessor USER_B = PemJwt.provider("1003000222");

  private String idOfA;
  private String idOfB;
  private MvcResult result;
  private MvcResult second;

  private MvcResult submit(RequestPostProcessor user, String npi, String type, String taxonomy)
      throws Exception {
    Map<String, Object> a = new LinkedHashMap<>();
    a.put("npi", npi);
    a.put("medicaidId", "M00000" + npi.substring(7));
    a.put("providerType", type);
    a.put("taxonomy", taxonomy);
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
    return mvc.perform(
            post("/api/v1/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(a))
                .with(user))
        .andReturn();
  }

  private String idOf(MvcResult r) throws Exception {
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
    return json.readTree(r.getResponse().getContentAsString()).path("applicationId").asText();
  }

  @Given("user A \\(PROVIDER, NPI {string}) submitted a valid application for NPI {string}")
  public void userA(String own, String npi) throws Exception {
    idOfA = idOf(submit(USER_A, npi, "IN", "207Q00000X"));
  }

  @Given("user B \\(PROVIDER, NPI {string}) submitted a valid application for NPI {string}")
  public void userB(String own, String npi) throws Exception {
    idOfB = idOf(submit(USER_B, npi, "IN", "207Q00000X"));
  }

  @Given(
      "user A submitted an application for NPI {string} with type {string} and an empty taxonomy")
  public void rejectedOfA(String npi, String type) throws Exception {
    assertThat(submit(USER_A, npi, type, "").getResponse().getStatus()).isEqualTo(422);
  }

  @Given("a REJECTED application loaded from a legacy file for NPI {string}")
  public void legacy(String npi) {
    Long id =
        jdbc.queryForObject(
            "INSERT INTO application (public_id, npi, medicaid_id, provider_type, taxonomy, status, source, submitted_by,"
                + " submitted_at) VALUES (?, ?, 'M00000333', 'IN', '', 'REJECTED', 'LEGACY_FILE', 'intake-run', now())"
                + " RETURNING id",
            Long.class,
            UUID.randomUUID(),
            npi);
    jdbc.update(
        "INSERT INTO application_error (application_id, code, field, message) VALUES (?, 'E104', 'taxonomy', 'Enter the taxonomy code.')",
        id);
  }

  private static RequestPostProcessor token(String role) {
    return switch (role) {
      case "PROVIDER" -> USER_A;
      case "ANALYST" -> PemJwt.analyst();
      case "SUPERVISOR" -> PemJwt.supervisor();
      case "ADMIN" -> PemJwt.admin();
      case "SYSTEM" -> PemJwt.system();
      default -> null;
    };
  }

  private MvcResult call(String uri, RequestPostProcessor token) throws Exception {
    MockHttpServletRequestBuilder req = get(uri);
    if (token != null) {
      req = req.with(token);
    }
    return mvc.perform(req).andReturn();
  }

  private JsonNode body() throws Exception {
    return json.readTree(result.getResponse().getContentAsString());
  }

  @When("user A lists the applications")
  public void listA() throws Exception {
    result = call("/api/v1/applications", USER_A);
  }

  @When("user A lists the applications for NPI {string}")
  public void listAFor(String npi) throws Exception {
    result = call("/api/v1/applications?npi=" + npi, USER_A);
  }

  @When("user A reads user B's application by id")
  public void readOthers() throws Exception {
    result = call("/api/v1/applications/" + idOfB, USER_A);
  }

  @When("user A reads their own application by id")
  public void readOwn() throws Exception {
    result = call("/api/v1/applications/" + idOfA, USER_A);
  }

  private List<String> rejectedIds() {
    return jdbc.queryForList(
        "SELECT public_id::text FROM application WHERE status = 'REJECTED' AND source = 'PORTAL' ORDER BY id",
        String.class);
  }

  @When("user A reads the first rejected application by id")
  public void readFirstRejected() throws Exception {
    result = call("/api/v1/applications/" + rejectedIds().get(0), USER_A);
  }

  @When("user A reads the second rejected application by id")
  public void readSecondRejected() throws Exception {
    result = call("/api/v1/applications/" + rejectedIds().get(1), USER_A);
  }

  @When("{string} lists the applications with status {string}")
  public void listStatus(String role, String status) throws Exception {
    result = call("/api/v1/applications?status=" + status, token(role));
  }

  @When("{string} lists the applications for NPI {string}")
  public void listNpi(String role, String npi) throws Exception {
    result = call("/api/v1/applications?npi=" + npi, token(role));
  }

  @When("{string} lists the applications with the query {string}")
  public void listQuery(String role, String query) throws Exception {
    result = call("/api/v1/applications?" + query, token(role));
  }

  @When("{string} reads the application {string}")
  public void readId(String role, String id) throws Exception {
    result = call("/api/v1/applications/" + id, token(role));
  }

  @When("{string} lists the applications and reads user A's application by id")
  public void listAndRead(String role) throws Exception {
    result = call("/api/v1/applications", token(role));
    second = call("/api/v1/applications/" + idOfA, token(role));
  }

  @Then("the list holds only the applications for NPI {string} and its total is {int}")
  public void onlyOwn(String npi, int total) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    body().path("items").forEach(i -> assertThat(i.path("npi").asText()).isEqualTo(npi));
    assertThat(body().path("total").asInt()).isEqualTo(total);
    assertThat(body().path("items")).hasSize(total);
  }

  @Then("the list is empty and its total is {int}")
  public void empty(int total) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(body().path("items")).isEmpty();
    assertThat(body().path("total").asInt()).isEqualTo(total);
  }

  @Then("the read answers {int} problem+json without {string}")
  public void problem(int status, String hidden) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(status);
    assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(result.getResponse().getContentAsString()).doesNotContain(hidden);
  }

  @Then(
      "the read answers 200 with status {string}, no errors, source {string}, NPI {string}, Medicaid ID {string}, type {string}, taxonomy {string}, the individual RIVERA MARIA MD12345 TX and a submission time")
  public void validated(
      String status, String source, String npi, String medicaidId, String type, String taxonomy)
      throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    JsonNode a = body();
    assertThat(a.path("applicationId").asText()).isEqualTo(idOfA);
    assertThat(a.path("status").asText()).isEqualTo(status);
    assertThat(a.path("errors").isMissingNode() || a.path("errors").isEmpty()).isTrue();
    assertThat(a.path("source").asText()).isEqualTo(source);
    assertThat(a.path("npi").asText()).isEqualTo(npi);
    assertThat(a.path("medicaidId").asText()).isEqualTo(medicaidId);
    assertThat(a.path("providerType").asText()).isEqualTo(type);
    assertThat(a.path("taxonomy").asText()).isEqualTo(taxonomy);
    assertThat(a.path("individual").path("lastName").asText()).isEqualTo("RIVERA");
    assertThat(a.path("individual").path("firstName").asText()).isEqualTo("MARIA");
    assertThat(a.path("individual").path("licenseNo").asText()).isEqualTo("MD12345");
    assertThat(a.path("individual").path("licenseState").asText()).isEqualTo("TX");
    assertThat(a.path("submittedAt").asText()).isNotBlank();
  }

  @Then("the read answers 200 with status {string} and exactly these errors: {string}")
  public void rejected(String status, String errors) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(body().path("status").asText()).isEqualTo(status);
    List<String> got = new ArrayList<>();
    body()
        .path("errors")
        .forEach(
            e -> {
              got.add(e.path("code").asText() + "/" + e.path("field").asText());
              assertThat(e.path("message").asText()).isNotBlank();
            });
    assertThat(got).containsExactlyInAnyOrder(errors.split(","));
  }

  @Then(
      "the list holds the REJECTED applications of NPI {string} from {string} and NPI {string} from {string}, with page 0 and size 20")
  public void allRejected(String npi1, String source1, String npi2, String source2)
      throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    List<String> got = new ArrayList<>();
    body()
        .path("items")
        .forEach(
            i -> {
              assertThat(i.path("status").asText()).isEqualTo("REJECTED");
              got.add(i.path("npi").asText() + "/" + i.path("source").asText());
            });
    assertThat(got).contains(npi1 + "/" + source1, npi2 + "/" + source2);
    assertThat(body().path("page").asInt()).isZero();
    assertThat(body().path("size").asInt()).isEqualTo(20);
    assertThat(body().path("total").asInt()).isEqualTo(got.size());
  }

  @Then("every listed application is for NPI {string}")
  public void allFor(String npi) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(body().path("items")).isNotEmpty();
    body().path("items").forEach(i -> assertThat(i.path("npi").asText()).isEqualTo(npi));
  }

  @Then("the list answers {int} problem+json")
  public void listProblem(int status) {
    assertThat(result.getResponse().getStatus()).isEqualTo(status);
    assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
  }

  @Then("the list answers {int}")
  public void listStatusOnly(int status) {
    assertThat(result.getResponse().getStatus()).isEqualTo(status);
  }

  @Then("both answers are {int}")
  public void both(int status) {
    assertThat(result.getResponse().getStatus()).isEqualTo(status);
    assertThat(second.getResponse().getStatus()).isEqualTo(status);
  }
}
