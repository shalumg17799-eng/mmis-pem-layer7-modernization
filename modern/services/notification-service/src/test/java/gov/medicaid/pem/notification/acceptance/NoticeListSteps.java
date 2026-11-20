package gov.medicaid.pem.notification.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.testsupport.PemJwt;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Steps for MAM-60 (list notices and notice templates). */
public class NoticeListSteps {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private MvcResult result;

  private void insert(String npi, String template, String status) {
    jdbc.update(
        "INSERT INTO notice (public_id, npi, template_code, channel, recipient, status, event_id, sent_at)"
            + " VALUES (?, ?, ?, 'EMAIL', ?, ?, ?, now())",
        UUID.randomUUID(),
        npi,
        template,
        npi + "@providers.pem.local",
        status,
        UUID.randomUUID());
  }

  @Given("the notice log holds:")
  public void holds(DataTable table) {
    jdbc.update("DELETE FROM notice");
    for (Map<String, String> r : table.asMaps()) {
      insert(r.get("npi"), r.get("template"), r.get("status"));
    }
  }

  @Given("the notice log is empty")
  public void empty() {
    jdbc.update("DELETE FROM notice");
  }

  @Given("the notice log also holds a {string} notice {string} for {string}")
  public void also(String status, String template, String npi) {
    insert(npi, template, status);
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

  private MvcResult call(String uri, String role) throws Exception {
    MockHttpServletRequestBuilder req = MockMvcRequestBuilders.get(uri);
    RequestPostProcessor t = token(role);
    if (t != null) {
      req = req.with(t);
    }
    return mvc.perform(req).andReturn();
  }

  @When("{string} lists the notices with {string}")
  public void list(String role, String query) throws Exception {
    result = call("/api/v1/notices" + (query.isEmpty() ? "" : "?" + query), role);
  }

  @When("{string} lists the notice templates")
  public void templates(String role) throws Exception {
    result = call("/api/v1/notice-templates", role);
  }

  private JsonNode body() throws Exception {
    return json.readTree(result.getResponse().getContentAsString());
  }

  @Then(
      "the notice list answers 200 with only notices of {string}, each with noticeId, npi, template, channel EMAIL, status and sentAt, page 0, size 20 and a total")
  public void only(String npi) throws Exception {
    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(200);
    JsonNode p = body();
    assertThat(p.path("items")).isNotEmpty();
    p.path("items")
        .forEach(
            n -> {
              assertThat(n.path("npi").asText()).isEqualTo(npi);
              assertThat(n.path("noticeId").asText()).isNotBlank();
              assertThat(n.path("template").asText()).isNotBlank();
              assertThat(n.path("channel").asText()).isEqualTo("EMAIL");
              assertThat(n.path("status").asText()).isNotBlank();
              assertThat(n.path("sentAt").asText()).isNotBlank();
            });
    assertThat(p.path("page").asInt()).isZero();
    assertThat(p.path("size").asInt()).isEqualTo(20);
    assertThat(p.path("total").asInt()).isEqualTo(p.path("items").size());
  }

  @Then("the notice list answers {int} problem+json")
  public void problem(int status) {
    assertThat(result.getResponse().getStatus()).isEqualTo(status);
    assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
  }

  @Then(
      "the notice list holds a notice for {string} with template {string}, channel {string} and status {string}")
  public void holdsNotice(String npi, String template, String channel, String status)
      throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    List<JsonNode> matches = new ArrayList<>();
    body()
        .path("items")
        .forEach(
            n -> {
              if (n.path("npi").asText().equals(npi)
                  && n.path("template").asText().equals(template)
                  && n.path("channel").asText().equals(channel)
                  && n.path("status").asText().equals(status)) {
                matches.add(n);
              }
            });
    assertThat(matches).isNotEmpty();
  }

  @Then("the notice list answers {int} and shows NPIs {string}")
  public void roles(int status, String npis) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(status);
    if (status == 200) {
      List<String> got = new ArrayList<>();
      body().path("items").forEach(n -> got.add(n.path("npi").asText()));
      assertThat(got).containsExactlyInAnyOrderElementsOf(Arrays.asList(npis.split(",")));
    }
  }

  @Then(
      "the template list answers 200 with exactly the codes {string}, each with code, subject and body")
  public void templateList(String codes) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    JsonNode list = body();
    assertThat(list.isArray()).isTrue();
    List<String> got = new ArrayList<>();
    list.forEach(
        t -> {
          got.add(t.path("code").asText());
          assertThat(t.path("subject").asText()).isNotBlank();
          assertThat(t.path("body").asText()).isNotBlank();
        });
    assertThat(got).containsExactlyInAnyOrder(codes.split(","));
  }

  @Then("the template list answers {int}")
  public void templateStatus(int status) {
    assertThat(result.getResponse().getStatus()).isEqualTo(status);
  }
}
