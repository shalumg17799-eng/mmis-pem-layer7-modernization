package gov.medicaid.pem.screening.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gov.medicaid.pem.screening.Bounded;
import gov.medicaid.pem.screening.Scrnout;
import gov.medicaid.pem.screening.ScreeningWorld;
import gov.medicaid.pem.testsupport.PemJwt;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Steps of MAM-32: the batch screening run, its SCRNOUT file and its counts. */
public class BatchRunSteps {

  private static final String RUNS = "/api/v1/batch/screening-runs";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private final List<String> startedRuns = new ArrayList<>();
  private MvcResult last;
  private String runId;
  private String firstRunId;
  private byte[] file;
  private boolean rejecting;

  private static RequestPostProcessor token(String role) {
    return switch (role) {
      case "ANALYST" -> PemJwt.analyst();
      case "SUPERVISOR" -> PemJwt.supervisor();
      case "ADMIN" -> PemJwt.admin();
      case "SYSTEM" -> PemJwt.system();
      case "PROVIDER" -> PemJwt.provider("1003000111");
      case "NONE" -> null;
      default -> throw new IllegalArgumentException(role);
    };
  }

  /** A run still going when the scenario ends would write into the next scenario's world. */
  @After(order = 20000)
  public void endRuns() throws Exception {
    for (String id : startedRuns) {
      waitForEnd(id);
    }
    if (rejecting) {
      jdbc.execute("DROP TRIGGER IF EXISTS reject_scrnout ON screening_run");
      jdbc.execute("DROP FUNCTION IF EXISTS reject_scrnout()");
    }
  }

  // ---- the mocked world -------------------------------------------------------------------

  @Given("provider-service lists these providers")
  public void lists(DataTable table) {
    List<ObjectNode> providers = new ArrayList<>();
    for (Map<String, String> row : table.asMaps()) {
      String npi = row.get("npi");
      ObjectNode p =
          ScreeningWorld.individual(
              npi, "TESTPROV", "P" + npi.substring(4), row.get("licence"), row.get("state"));
      providers.add(ScreeningWorld.withFlag(p, row.get("screenFlag")));
      ScreeningWorld.licence(row.get("licence"), row.get("state"), "ACTIVE", "2028-09", 3);
    }
    ScreeningWorld.lists(providers);
  }

  @Given("provider-service lists {int} providers of which {int} are unscreened")
  public void listsMany(int total, int unscreened) {
    List<ObjectNode> providers = new ArrayList<>();
    for (int i = 1; i <= total; i++) {
      String npi = String.format("1004%06d", i);
      // the first, number 250 and the last are unscreened: one on each of the three pages of 200
      boolean due = i == 1 || i == total || (unscreened > 2 && i == 250);
      providers.add(
          ScreeningWorld.withFlag(
              ScreeningWorld.individual(npi, "TESTPROV", "N" + i, "LIC" + i, "TX"),
              due ? "U" : "P"));
    }
    ScreeningWorld.lists(providers);
  }

  @Given("provider-service answers HTTP {int} for the provider list")
  public void listDown(int status) {
    ScreeningWorld.listAnswers(status);
  }

  @Given("the database rejects the writing of the SCRNOUT file")
  public void rejectFile() {
    jdbc.execute(
        "CREATE FUNCTION reject_scrnout() RETURNS trigger AS $$ BEGIN"
            + " RAISE EXCEPTION 'the test database rejected this write'; END; $$ LANGUAGE plpgsql");
    jdbc.execute(
        "CREATE TRIGGER reject_scrnout BEFORE UPDATE ON screening_run FOR EACH ROW"
            + " WHEN (NEW.status = 'COMPLETED') EXECUTE FUNCTION reject_scrnout()");
    rejecting = true;
  }

  // ---- starting a run ---------------------------------------------------------------------

  private MvcResult start(String role, String body) throws Exception {
    MockHttpServletRequestBuilder req =
        MockMvcRequestBuilders.post(RUNS).contentType(MediaType.APPLICATION_JSON).content(body);
    RequestPostProcessor t = token(role);
    MvcResult r = mvc.perform(t == null ? req : req.with(t)).andReturn();
    if (r.getResponse().getStatus() == 202) {
      startedRuns.add(json.readTree(r.getResponse().getContentAsString()).path("runId").asText());
    }
    return r;
  }

  @When("{string} starts a screening run for business date {string}")
  public void starts(String role, String date) throws Exception {
    last = start(role, "{\"businessDate\":\"" + date + "\"}");
  }

  @When("{string} starts a screening run without a business date")
  public void startsWithoutDate(String role) throws Exception {
    last = start(role, "{}");
  }

  private JsonNode waitForEnd(String id) throws Exception {
    JsonNode[] run = new JsonNode[1];
    Bounded.until(
        "screening run " + id + " has ended",
        () -> {
          MvcResult r =
              mvc.perform(MockMvcRequestBuilders.get(RUNS + "/" + id).with(PemJwt.admin()))
                  .andReturn();
          run[0] = json.readTree(r.getResponse().getContentAsString());
          return r.getResponse().getStatus() == 200
              && !"RUNNING".equals(run[0].path("status").asText());
        });
    return run[0];
  }

  @When("ADMIN runs the screening for business date {string} and waits for its end")
  @Given("ADMIN has run the screening for business date {string} and the run has ended")
  public void runs(String date) throws Exception {
    last = start("ADMIN", "{\"businessDate\":\"" + date + "\"}");
    assertThat(last.getResponse().getStatus()).isEqualTo(202);
    runId = json.readTree(last.getResponse().getContentAsString()).path("runId").asText();
    if (firstRunId == null) {
      firstRunId = runId;
    }
    waitForEnd(runId);
  }

  @Then("the started runs have ended")
  public void started() throws Exception {
    for (String id : startedRuns) {
      assertThat(waitForEnd(id).path("status").asText()).isNotEqualTo("RUNNING");
    }
  }

  // ---- the start answer -------------------------------------------------------------------

  @Then("the start answers {int}")
  public void startAnswers(int status) {
    assertThat(last.getResponse().getStatus())
        .as(last.getResponse().getContentAsString())
        .isEqualTo(status);
  }

  @Then("the start answers {int} problem+json")
  public void startProblem(int status) {
    assertThat(last.getResponse().getStatus())
        .as(last.getResponse().getContentAsString())
        .isEqualTo(status);
    assertThat(last.getResponse().getContentType()).startsWith("application/problem+json");
  }

  @Then(
      "the start answers 202 with a BatchRun for job {string}, business date {string} and status {string}")
  public void startedRun(String job, String date, String status) throws Exception {
    assertThat(last.getResponse().getStatus()).isEqualTo(202);
    JsonNode run = json.readTree(last.getResponse().getContentAsString());
    assertThat(UUID.fromString(run.path("runId").asText())).isNotNull();
    assertThat(run.path("job").asText()).isEqualTo(job);
    assertThat(run.path("businessDate").asText()).isEqualTo(date);
    assertThat(run.path("status").asText()).isEqualTo(status);
  }

  @Then("no screening run is stored")
  public void noRun() {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM screening_run", Integer.class)).isZero();
  }

  // ---- what the run did -------------------------------------------------------------------

  private JsonNode run(String id) throws Exception {
    MvcResult r =
        mvc.perform(MockMvcRequestBuilders.get(RUNS + "/" + id).with(PemJwt.admin())).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString());
  }

  private List<JsonNode> allScreenings() throws Exception {
    MvcResult r =
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/screenings?size=200").with(PemJwt.analyst()))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    List<JsonNode> all = new ArrayList<>();
    json.readTree(r.getResponse().getContentAsString()).path("items").forEach(all::add);
    return all;
  }

  @Then("the run has created INITIAL screenings for exactly {string}")
  public void createdFor(String npis) throws Exception {
    List<JsonNode> screenings = allScreenings();
    assertThat(screenings)
        .allSatisfy(s -> assertThat(s.path("reason").asText()).isEqualTo("INITIAL"));
    assertThat(screenings.stream().map(s -> s.path("npi").asText()).sorted().toList())
        .containsExactlyElementsOf(Arrays.asList(npis.split(",")));
  }

  @Then("no screening exists for {string} or {string}")
  public void noneFor(String first, String second) throws Exception {
    assertThat(allScreenings().stream().map(s -> s.path("npi").asText()))
        .doesNotContain(first, second);
  }

  @Then("provider-service was asked for page {string} of the provider list exactly once")
  public void pageOnce(String page) {
    assertThat(ScreeningWorld.listedPages()).containsOnlyOnce(page);
  }

  @Then("provider-service was not asked for any other page of the provider list")
  public void noOtherPage() {
    assertThat(ScreeningWorld.listedPages()).containsExactly("0");
  }

  @Then("provider-service was asked for the pages {string} of the provider list, each exactly once")
  public void pages(String pages) {
    assertThat(ScreeningWorld.listedPages()).containsExactlyElementsOf(Arrays.asList(pages.split(",")));
  }

  @Then("provider-service was asked for the provider list at most {int} times")
  public void listAtMost(int times) {
    assertThat(ScreeningWorld.listedPages()).hasSizeLessThanOrEqualTo(times);
  }

  @Then("the run is {string} with counts read {int}, screened {int}, skipped {int} and failed {int}")
  public void counts(String status, int read, int screened, int skipped, int failed)
      throws Exception {
    JsonNode run = run(runId);
    assertThat(run.path("status").asText()).as(run.toString()).isEqualTo(status);
    JsonNode counts = run.path("counts");
    assertThat(counts.path("read").asInt()).as("read").isEqualTo(read);
    assertThat(counts.path("screened").asInt()).as("screened").isEqualTo(screened);
    assertThat(counts.path("skipped").asInt()).as("skipped").isEqualTo(skipped);
    assertThat(counts.path("failed").asInt()).as("failed").isEqualTo(failed);
  }

  @Then("the run has an end time and no error")
  public void endTimeNoError() throws Exception {
    JsonNode run = run(runId);
    assertThat(run.path("endedAt").asText()).isNotBlank();
    assertThat(run.path("error").isMissingNode() || run.path("error").isNull()).isTrue();
  }

  @Then("the run is {string} with a short reason in error and an end time")
  public void failedRun(String status) throws Exception {
    JsonNode run = run(runId);
    assertThat(run.path("status").asText()).as(run.toString()).isEqualTo(status);
    assertThat(run.path("error").asText()).isNotBlank();
    assertThat(run.path("endedAt").asText()).isNotBlank();
  }

  @Then("the run has no results file")
  public void noFile() throws Exception {
    MvcResult r =
        mvc.perform(
                MockMvcRequestBuilders.get(RUNS + "/" + runId + "/results-file")
                    .with(PemJwt.admin()))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(404);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
  }

  // ---- the file ---------------------------------------------------------------------------

  @When("ADMIN downloads the results file of the run")
  public void downloads() throws Exception {
    MvcResult r =
        mvc.perform(
                MockMvcRequestBuilders.get(RUNS + "/" + runId + "/results-file")
                    .with(PemJwt.admin()))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    file = r.getResponse().getContentAsByteArray();
  }

  @Then("the results file has {int} records of 67 bytes")
  public void recordCount(int count) {
    assertThat(file).hasSize(count * Scrnout.LENGTH);
    Scrnout.assertFixedLengthAscii(file);
  }

  @Then(
      "the results file lists the providers {string} in ascending NPI order, each with its four rows in the order LICBOARD, OIGLEIE, SAMGOV and SSADMF")
  public void order(String npis) {
    List<Scrnout.Row> rows = Scrnout.rows(file);
    List<String> expected = new ArrayList<>();
    for (String npi : npis.split(",")) {
      for (String source : ScreeningWorld.SOURCES) {
        expected.add(npi + "/" + source);
      }
    }
    assertThat(rows.stream().map(r -> r.npi() + "/" + r.source().trim()).toList())
        .containsExactlyElementsOf(expected);
  }

  @Then(
      "every record of the results file repeats the stored result of its source with the verdict date {string}, the source left-justified and the detail cut to 40 characters")
  public void repeats(String date) throws Exception {
    for (Scrnout.Row row : Scrnout.rows(file)) {
      MvcResult r =
          mvc.perform(
                  MockMvcRequestBuilders.get("/api/v1/screenings?npi=" + row.npi())
                      .with(PemJwt.analyst()))
              .andReturn();
      JsonNode screening = json.readTree(r.getResponse().getContentAsString()).path("items").get(0);
      JsonNode stored = null;
      for (JsonNode x : screening.path("results")) {
        if (x.path("source").asText().equals(row.source().trim())) {
          stored = x;
        }
      }
      assertThat(stored).as(row.npi() + " " + row.source()).isNotNull();
      assertThat(row.text())
          .isEqualTo(
              Scrnout.record(
                  row.npi(),
                  stored.path("source").asText(),
                  stored.path("result").asText(),
                  date,
                  stored.path("detail").asText()));
    }
  }

  private String resultOf(String npi, String source) {
    return Scrnout.rows(file).stream()
        .filter(r -> r.npi().equals(npi) && r.source().trim().equals(source))
        .map(Scrnout.Row::result)
        .findFirst()
        .orElseThrow(() -> new AssertionError("no row " + npi + " " + source));
  }

  @Then("the results file row of {string} for {string} has result {string}")
  public void rowResult(String npi, String source, String result) {
    assertThat(resultOf(npi, source)).isEqualTo(result);
  }

  @Then(
      "the results file rows of {string} for {string}, {string} and {string} have result {string}")
  public void rowsResult(String npi, String a, String b, String c, String result) {
    for (String source : List.of(a, b, c)) {
      assertThat(resultOf(npi, source)).as(source).isEqualTo(result);
    }
  }

  @Then("the results file rows of {string} all have result {string}")
  public void allRows(String npi, String result) {
    for (String source : ScreeningWorld.SOURCES) {
      assertThat(resultOf(npi, source)).as(source).isEqualTo(result);
    }
  }

  // ---- running again ----------------------------------------------------------------------

  @Then(
      "the second run has created no screening and no ScreeningCompleted event for {string} or {string}")
  public void secondRunNothing(String first, String second) throws Exception {
    assertThat(runId).isNotEqualTo(firstRunId);
    for (String npi : List.of(first, second)) {
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM screening s JOIN screening_run r ON r.id = s.run_id"
                      + " WHERE r.public_id = ?::uuid AND s.npi = ?",
                  Integer.class,
                  runId,
                  npi))
          .isZero();
    }
  }

  @Then("the SCRNOUT file of the second run is empty")
  public void secondFileEmpty() throws Exception {
    downloads();
    assertThat(file).isEmpty();
  }

  @Then("exactly {int} INITIAL screening and {int} ScreeningCompleted event exist for {string}")
  public void onlyOnce(int screenings, int events, String npi) {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM screening WHERE npi = ? AND reason = 'INITIAL'",
                Integer.class,
                npi))
        .isEqualTo(screenings);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE type = 'ScreeningCompleted' AND subject = ?",
                Integer.class,
                npi))
        .isEqualTo(events);
  }

  // ---- downloading the file ---------------------------------------------------------------

  @When("{string} downloads the results file of that run")
  public void downloadAs(String role) throws Exception {
    MockHttpServletRequestBuilder req =
        MockMvcRequestBuilders.get(RUNS + "/" + firstRunId + "/results-file");
    RequestPostProcessor t = token(role);
    last = mvc.perform(t == null ? req : req.with(t)).andReturn();
  }

  @When("{string} downloads the results file of an unknown run")
  public void downloadUnknown(String role) throws Exception {
    last =
        mvc.perform(
                MockMvcRequestBuilders.get(RUNS + "/" + UUID.randomUUID() + "/results-file")
                    .with(token(role)))
            .andReturn();
  }

  @Then("the download answers {int}")
  public void downloadAnswers(int status) {
    assertThat(last.getResponse().getStatus()).isEqualTo(status);
  }

  @Then("the download answers {int} problem+json")
  public void downloadProblem(int status) {
    assertThat(last.getResponse().getStatus()).isEqualTo(status);
    assertThat(last.getResponse().getContentType()).startsWith("application/problem+json");
  }
}
