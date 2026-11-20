package gov.medicaid.pem.sanctions.acceptance;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import gov.medicaid.pem.sanctions.IntakeApi;
import gov.medicaid.pem.sanctions.SancinFile;
import gov.medicaid.pem.sanctions.TestProviderService;
import gov.medicaid.pem.testsupport.PemJwt;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.awaitility.Awaitility;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Steps for MAM-55 (the SANCIN intake run). The run goes on in a background thread of the service,
 * so a scenario waits for its end with "the run has ended"; provider-service is the WireMock of
 * TestProviderService (the steps that arrange it and check it are those of MAM-52).
 */
public class IntakeSteps {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private final ListAppender<ILoggingEvent> log = new ListAppender<>();
  private final List<String> records = new ArrayList<>();
  private final List<String> startedRuns = new ArrayList<>();
  private String lineEnds = "LF";
  private MvcResult answer;
  private String runId;
  private JsonNode ended;

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

  // ---- hooks -----------------------------------------------------------------------------------

  @Before("@MAM-55")
  public void captureLog() {
    log.start();
    ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).addAppender(log);
  }

  /** A run still going on must not meet the next scenario's clean-up. */
  @After("@MAM-55")
  public void waitForTheRunsAndReleaseTheLog() throws Exception {
    try {
      for (String id : startedRuns) {
        IntakeApi.await(mvc, json, id);
      }
    } finally {
      ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).detachAppender(log);
    }
  }

  // ---- given -----------------------------------------------------------------------------------

  @Given("the sanction intake runs, history and outbox are empty")
  public void empty() {
    jdbc.update("DELETE FROM sanction");
    jdbc.update("DELETE FROM sanction_intake_run");
    jdbc.update("DELETE FROM outbox_event");
    TestProviderService.reset();
  }

  @Given("a SANCIN file with these records:")
  public void file(DataTable table) {
    records.clear();
    for (Map<String, String> row : table.asMaps()) {
      records.add(
          SancinFile.record(
              row.get("npi"), row.get("action"), row.get("reason"), row.get("effectiveDate")));
    }
  }

  @Given("the file is written with line ends {string}")
  public void lineEnds(String ends) {
    lineEnds = ends;
  }

  @Given("record {int} of the file is cut to {int} bytes")
  public void cut(int number, int bytes) {
    records.set(number - 1, SancinFile.cut(records.get(number - 1), bytes));
  }

  private byte[] bytes() {
    String[] all = records.toArray(new String[0]);
    return switch (lineEnds) {
      case "CRLF" -> SancinFile.crlf(all);
      case "NONE" -> SancinFile.blocks(all);
      default -> SancinFile.lines(all);
    };
  }

  // ---- when ------------------------------------------------------------------------------------

  private void start(String role, byte[] file, String date) throws Exception {
    answer = IntakeApi.start(mvc, token(role), file, date);
    if (answer.getResponse().getStatus() == 202) {
      runId = body().path("runId").asText();
      startedRuns.add(runId);
    }
  }

  @When("{string} starts a sanction intake run with the file for business date {string}")
  public void starts(String role, String date) throws Exception {
    start(role, bytes(), date);
  }

  @When("{string} starts a sanction intake run with the file and no business date")
  public void startsWithoutDate(String role) throws Exception {
    start(role, bytes(), null);
  }

  @When("{string} starts a sanction intake run for business date {string} without the file")
  public void startsWithoutFile(String role, String date) throws Exception {
    start(role, null, date);
  }

  @When("the run has ended")
  public void hasEnded() throws Exception {
    ended = IntakeApi.await(mvc, json, runId);
  }

  @When("{string} reads the run {string}")
  public void reads(String role, String id) throws Exception {
    answer = IntakeApi.read(mvc, id, token(role));
  }

  // ---- the answers -----------------------------------------------------------------------------

  private JsonNode body() throws Exception {
    return json.readTree(answer.getResponse().getContentAsString());
  }

  private void status(int expected) throws Exception {
    assertThat(answer.getResponse().getStatus())
        .as(answer.getResponse().getContentAsString())
        .isEqualTo(expected);
  }

  private void problem(int expected) throws Exception {
    status(expected);
    assertThat(answer.getResponse().getContentType()).startsWith("application/problem+json");
  }

  @Then(
      "the start answer is 202 with a run id, job {string}, business date {string} and status RUNNING or COMPLETED")
  public void accepted(String job, String date) throws Exception {
    status(202);
    JsonNode run = body();
    assertThat(run.path("runId").asText()).matches("[0-9a-f-]{36}");
    assertThat(run.path("job").asText()).isEqualTo(job);
    assertThat(run.path("businessDate").asText()).isEqualTo(date);
    assertThat(run.path("status").asText()).isIn("RUNNING", "COMPLETED");
  }

  @Then("reading the run as {string} shows business date {string}")
  public void readShows(String role, String date) throws Exception {
    MvcResult r = IntakeApi.read(mvc, runId, token(role));
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
    assertThat(json.readTree(r.getResponse().getContentAsString()).path("businessDate").asText())
        .isEqualTo(date);
  }

  @Then("the start answer is {int} problem+json whose detail names {string}")
  public void startProblemNaming(int code, String text) throws Exception {
    problem(code);
    assertThat(body().path("detail").asText()).contains(text);
  }

  @Then("the start answer is {int} problem+json")
  public void startProblem(int code) throws Exception {
    problem(code);
  }

  @Then("the start answer is {int}")
  public void startAnswer(int code) throws Exception {
    status(code);
  }

  @Then("the read answer is {int} problem+json whose detail names {string}")
  public void readProblemNaming(int code, String text) throws Exception {
    problem(code);
    assertThat(body().path("detail").asText()).contains(text);
  }

  @Then("reading the run as {string} answers {int}")
  public void readAnswers(String role, int code) throws Exception {
    MvcResult r = IntakeApi.read(mvc, runId, token(role));
    assertThat(r.getResponse().getStatus())
        .as(r.getResponse().getContentAsString())
        .isEqualTo(code);
  }

  @Then("reading the run as {string} answers {int} problem+json")
  public void readAnswersProblem(String role, int code) throws Exception {
    MvcResult r = IntakeApi.read(mvc, runId, token(role));
    assertThat(r.getResponse().getStatus())
        .as(r.getResponse().getContentAsString())
        .isEqualTo(code);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
  }

  @Then("no sanction intake run exists")
  public void noRun() {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM sanction_intake_run", Integer.class))
        .isZero();
  }

  // ---- the run ---------------------------------------------------------------------------------

  @Then("the run is COMPLETED with an endedAt and no error")
  public void completed() {
    assertThat(ended.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(ended.path("endedAt").asText()).isNotBlank();
    assertThat(ended.path("error").isMissingNode() || ended.path("error").isNull())
        .as("error of %s", ended)
        .isTrue();
  }

  @Then("the run is FAILED with an endedAt and an error naming {string}")
  public void failed(String text) {
    assertThat(ended.path("status").asText()).isEqualTo("FAILED");
    assertThat(ended.path("endedAt").asText()).isNotBlank();
    assertThat(ended.path("error").asText()).contains(text);
  }

  @Then("the run is FAILED with an endedAt and an error naming {string} and {string}")
  public void failedNaming(String first, String second) {
    failed(first);
    assertThat(ended.path("error").asText()).contains(second);
  }

  @Then("the run counts are:")
  public void counts(DataTable table) {
    Map<String, String> expected = table.asMaps().get(0);
    JsonNode counts = ended.path("counts");
    expected.forEach(
        (name, value) ->
            assertThat(counts.path(name).asInt(-1))
                .as("counts.%s of %s", name, ended)
                .isEqualTo(Integer.parseInt(value)));
  }

  // ---- provider-service ------------------------------------------------------------------------

  @Then(
      "provider-service received exactly {int} status change(s) for {string} with action {string}, effective date {string} and the SYSTEM token")
  public void receivedWithToken(int count, String npi, String action, String effectiveDate)
      throws Exception {
    List<LoggedRequest> calls =
        TestProviderService.MOCK.findAll(
            postRequestedFor(urlPathEqualTo("/api/v1/providers/" + npi + "/status-changes")));
    assertThat(calls).hasSize(count);
    JsonNode sent = json.readTree(calls.get(0).getBodyAsString());
    assertThat(sent.path("action").asText()).isEqualTo(action);
    assertThat(sent.path("effectiveDate").asText()).isEqualTo(effectiveDate);
    assertThat(sent.path("sanctionId").asText()).matches("[0-9a-f-]{36}");
    assertThat(calls.get(0).getHeader("Authorization"))
        .isEqualTo("Bearer " + TestProviderService.SYSTEM_TOKEN);
  }

  // ---- history and events ----------------------------------------------------------------------

  private JsonNode history(String npi) throws Exception {
    var request = get("/api/v1/sanctions").param("size", "200").with(PemJwt.admin());
    if (npi != null) {
      request = request.param("npi", npi);
    }
    MvcResult r = mvc.perform(request).andReturn();
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString());
  }

  @Then(
      "the sanction history of {string} shows {int} row(s) with effective date {string} and reason {string}")
  public void historyShows(String npi, int rows, String effectiveDate, String reason)
      throws Exception {
    JsonNode page = history(npi);
    assertThat(page.path("total").asInt()).isEqualTo(rows);
    for (JsonNode item : page.path("items")) {
      assertThat(item.path("effectiveDate").asText()).isEqualTo(effectiveDate);
      assertThat(item.path("reason").asText()).isEqualTo(reason);
    }
  }

  @Then("the sanction history holds {int} row(s) for the NPIs {string}")
  public void historyHolds(int rows, String npis) throws Exception {
    JsonNode page = history(null);
    assertThat(page.path("total").asInt()).isEqualTo(rows);
    TreeSet<String> actual = new TreeSet<>();
    page.path("items").forEach(i -> actual.add(i.path("npi").asText()));
    assertThat(actual)
        .containsExactlyElementsOf(new TreeSet<>(Arrays.stream(npis.split(",\\s*")).toList()));
  }

  @Then("every sanction history row has sourceFeed {string} and recordedBy {string}")
  public void historySource(String sourceFeed, String recordedBy) throws Exception {
    JsonNode items = history(null).path("items");
    assertThat(items).isNotEmpty();
    for (JsonNode item : items) {
      assertThat(item.path("sourceFeed").asText()).isEqualTo(sourceFeed);
      assertThat(item.path("recordedBy").asText()).isEqualTo(recordedBy);
    }
  }

  @Then("the SanctionApplied event for {string} carries the sanctionId of its history row")
  public void eventCarriesSanctionId(String npi) throws Exception {
    String sanctionId = history(npi).path("items").get(0).path("sanctionId").asText();
    List<String> payloads =
        jdbc.queryForList(
            "SELECT payload::text FROM outbox_event WHERE type = 'SanctionApplied' AND subject = ?",
            String.class,
            npi);
    assertThat(payloads).hasSize(1);
    assertThat(json.readTree(payloads.get(0)).path("data").path("sanctionId").asText())
        .isEqualTo(sanctionId);
  }

  // ---- the service log -------------------------------------------------------------------------

  private List<String> messages() {
    synchronized (log) {
      return log.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
  }

  @Then("the service log contains the line {string}")
  public void logContains(String line) {
    Awaitility.await("log line '" + line + "'")
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(100))
        .until(() -> messages().contains(line));
    assertThat(messages()).contains(line);
  }
}
