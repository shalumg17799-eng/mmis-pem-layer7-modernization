package gov.medicaid.pem.provider.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.provider.LogCapture;
import gov.medicaid.pem.testsupport.OpenApiContract;
import gov.medicaid.pem.testsupport.PemJwt;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Requests and answers shared by the scenarios of MAM-37 and MAM-40: callers send requests, the
 * answers are kept in order ("answer 1" is the first request of the scenario), and the failure of
 * the provider database is simulated by taking the provider table away for the scenario.
 */
public class HttpAnswerSteps {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private final List<MvcResult> answers = new ArrayList<>();
  private LogCapture log;
  private boolean tableAway;

  @Before("@MAM-37 or @MAM-40")
  public void startCapture() {
    answers.clear();
    log = LogCapture.start();
  }

  @After("@MAM-37 or @MAM-40")
  public void stopCapture() {
    if (tableAway) {
      jdbc.execute("ALTER TABLE provider_unreadable RENAME TO provider");
      tableAway = false;
    }
    if (log != null) {
      log.close();
    }
  }

  /**
   * The caller of a request: a staff role (ANALYST, SUPERVISOR, ADMIN, SYSTEM), PROVIDER:{npi} (a
   * provider whose token carries that npi claim), or NONE (no token).
   */
  static RequestPostProcessor caller(String who) {
    if (who.startsWith("PROVIDER:")) {
      return PemJwt.provider(who.substring("PROVIDER:".length()));
    }
    return switch (who) {
      case "ANALYST" -> PemJwt.analyst();
      case "SUPERVISOR" -> PemJwt.supervisor();
      case "ADMIN" -> PemJwt.admin();
      case "SYSTEM" -> PemJwt.system();
      case "NONE" -> null;
      default -> throw new IllegalArgumentException("unknown caller " + who);
    };
  }

  /** Path and query of a request target; query values are taken literally (no URL decoding). */
  static MockHttpServletRequestBuilder request(String method, String target) {
    int q = target.indexOf('?');
    String path = q < 0 ? target : target.substring(0, q);
    MockHttpServletRequestBuilder b =
        MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path);
    if (q >= 0) {
      for (String pair : target.substring(q + 1).split("&")) {
        int eq = pair.indexOf('=');
        b.param(pair.substring(0, eq), pair.substring(eq + 1));
      }
    }
    return b;
  }

  private void send(String who, String method, String target, String correlationId)
      throws Exception {
    MockHttpServletRequestBuilder b = request(method, target);
    RequestPostProcessor token = caller(who);
    if (token != null) {
      b = b.with(token);
    }
    if (correlationId != null) {
      b = b.header("X-Correlation-Id", correlationId);
    }
    answers.add(mvc.perform(b).andReturn());
  }

  @When("a(n) {string} caller sends {word} {string}")
  public void sends(String who, String method, String target) throws Exception {
    send(who, method, target, null);
  }

  @When("a(n) {string} caller sends {word} {string} with correlation id {string}")
  public void sendsWithCorrelationId(String who, String method, String target, String correlationId)
      throws Exception {
    send(who, method, target, correlationId);
  }

  /** The provider table is renamed for the scenario: every read of a provider fails. */
  @Given("the provider records cannot be read")
  public void recordsCannotBeRead() {
    jdbc.execute("ALTER TABLE provider RENAME TO provider_unreadable");
    tableAway = true;
  }

  MvcResult answer(int n) {
    assertThat(answers).as("answers so far").hasSizeGreaterThanOrEqualTo(n);
    return answers.get(n - 1);
  }

  JsonNode body(int n) throws Exception {
    return json.readTree(answer(n).getResponse().getContentAsString());
  }

  @Then("answer {int} has status {int}")
  public void hasStatus(int n, int status) throws Exception {
    assertThat(answer(n).getResponse().getStatus())
        .as(answer(n).getResponse().getContentAsString())
        .isEqualTo(status);
  }

  @Then("answer {int} has status {int} and media type {string}")
  public void hasStatusAndMediaType(int n, int status, String media) throws Exception {
    hasStatus(n, status);
    assertThat(answer(n).getResponse().getContentType()).startsWith(media);
  }

  @Then("answer {int} is a server error and not a 404")
  public void serverError(int n) {
    int status = answer(n).getResponse().getStatus();
    assertThat(status).isBetween(500, 599);
  }

  @Then("answer {int} is a problem+json with status {int}")
  public void isProblem(int n, int status) throws Exception {
    hasStatusAndMediaType(n, status, "application/problem+json");
    assertThat(body(n).path("status").asInt()).isEqualTo(status);
  }

  /** Rows of "| /json/pointer | value |"; the value &lt;absent&gt; means the field is not there. */
  @Then("answer {int} has these fields:")
  public void hasFields(int n, DataTable table) throws Exception {
    JsonNode b = body(n);
    for (List<String> row : table.asLists()) {
      JsonNode v = b.at(row.get(0));
      if ("<absent>".equals(row.get(1))) {
        assertThat(v.isMissingNode() || v.isNull()).as("%s is absent", row.get(0)).isTrue();
      } else {
        assertThat(v.isMissingNode()).as("%s is present", row.get(0)).isFalse();
        assertThat(v.asText()).as(row.get(0)).isEqualTo(row.get(1));
      }
    }
  }

  @Then("answer {int} does not contain {string}")
  public void doesNotContain(int n, String text) throws Exception {
    assertThat(answer(n).getResponse().getContentAsString()).doesNotContain(text);
  }

  @Then("answer {int} carries the header {string} with value {string}")
  public void header(int n, String name, String value) {
    assertThat(answer(n).getResponse().getHeader(name)).isEqualTo(value);
  }

  @Then("answer {int} matches the provider-service contract for {word}")
  public void matchesContract(int n, String operationId) throws Exception {
    OpenApiContract.of("provider-service")
        .assertResponse(
            operationId,
            answer(n).getResponse().getStatus(),
            answer(n).getResponse().getContentAsString());
  }

  @Then("the failure is logged at ERROR with correlation id {string}")
  public void failureLogged(String correlationId) {
    assertThat(log.lines())
        .anyMatch(
            l ->
                l.level() == Level.ERROR
                    && l.logger().endsWith("ProviderProblems")
                    && correlationId.equals(l.correlationId()));
  }
}
