package gov.medicaid.pem.directory.acceptance;

import static gov.medicaid.pem.directory.TestSources.MOCK;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.directory.DirectoryFixtures;
import gov.medicaid.pem.directory.Dirout;
import gov.medicaid.pem.directory.TestSources;
import gov.medicaid.pem.testsupport.PemJwt;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Steps for MAM-67 (the PRVDIR01 directory extract as a Spring Batch job, DIROUT file). The
 * provider-service and Kafka steps of MAM-61 (DirectorySteps) and the "directory holds" step of
 * MAM-63 are reused.
 */
public class DirectoryExtractSteps {

  static final String BASE = "/api/v1/batch/directory-extracts";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private MvcResult start;
  private final List<UUID> runIds = new ArrayList<>();
  private byte[] file;
  private int rememberedRuns;
  private MvcResult callerStart;
  private MvcResult callerDownload;
  private UUID earlierRun;

  @Before("@MAM-67")
  public void reset() {
    MOCK.resetAll();
    TestSources.stubToken();
    DirectoryFixtures.clear(jdbc);
    dropRejectingTrigger();
  }

  @After("@MAM-67")
  public void cleanUp() {
    dropRejectingTrigger();
  }

  private void dropRejectingTrigger() {
    jdbc.execute("DROP TRIGGER IF EXISTS reject_completed_run ON extract_run");
    jdbc.execute("DROP FUNCTION IF EXISTS reject_completed_run()");
  }

  // ---- calls ----------------------------------------------------------------------------------

  private MvcResult post(RequestPostProcessor who, String body) throws Exception {
    MockHttpServletRequestBuilder request =
        MockMvcRequestBuilders.post(BASE).contentType(MediaType.APPLICATION_JSON).content(body);
    return mvc.perform(who == null ? request : request.with(who)).andReturn();
  }

  private MvcResult download(RequestPostProcessor who, UUID runId) throws Exception {
    MockHttpServletRequestBuilder request =
        MockMvcRequestBuilders.get(BASE + "/" + runId + "/file");
    return mvc.perform(who == null ? request : request.with(who)).andReturn();
  }

  private JsonNode body(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString());
  }

  private static String startBody(String date) {
    return "{\"businessDate\":\"" + date + "\"}";
  }

  private UUID started(MvcResult r) throws Exception {
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(202);
    return UUID.fromString(body(r).path("runId").asText());
  }

  @When("the ADMIN starts the directory extract for {string}")
  public void adminStarts(String date) throws Exception {
    start = post(PemJwt.admin(), startBody(date));
    if (start.getResponse().getStatus() == 202) {
      runIds.add(started(start));
    }
  }

  @When("the ADMIN starts the directory extract for {string} again")
  public void adminStartsAgain(String date) throws Exception {
    adminStarts(date);
  }

  @When("the ADMIN starts the directory extract with the body {string}")
  public void adminStartsWithBody(String body) throws Exception {
    start = post(PemJwt.admin(), body);
  }

  @When("the ADMIN downloads the file of the run")
  public void adminDownloads() throws Exception {
    MvcResult r = download(PemJwt.admin(), runIds.get(runIds.size() - 1));
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    file = r.getResponse().getContentAsByteArray();
  }

  // ---- AC-1 and AC-2 --------------------------------------------------------------------------

  @Then(
      "the extract answers 202 with a run for the job {string}, business date {string} and status {string}")
  public void answer(String job, String date, String status) throws Exception {
    assertThat(start.getResponse().getStatus())
        .as(start.getResponse().getContentAsString())
        .isEqualTo(202);
    JsonNode run = body(start);
    assertThat(run.path("job").asText()).isEqualTo(job);
    assertThat(run.path("businessDate").asText()).isEqualTo(date);
    assertThat(run.path("status").asText()).isEqualTo(status);
  }

  @Then("the run has a run id, a start time and an end time")
  public void times() throws Exception {
    JsonNode run = body(start);
    assertThat(UUID.fromString(run.path("runId").asText())).isNotNull();
    Instant started = Instant.parse(run.path("startedAt").asText());
    Instant ended = Instant.parse(run.path("endedAt").asText());
    assertThat(ended).isAfterOrEqualTo(started);
  }

  @Then("the run counts {int} rows")
  public void rows(int rows) throws Exception {
    assertThat(body(start).path("counts").path("rows").asInt()).isEqualTo(rows);
  }

  @Then("the downloaded file of the run holds {int} record(s), as many as the rows counted")
  public void recordsEqualRows(int records) throws Exception {
    adminDownloads();
    assertThat(Dirout.records(file)).hasSize(records);
    assertThat(body(start).path("counts").path("rows").asInt()).isEqualTo(records);
  }

  @Given("the number of directory extract runs is remembered")
  public void rememberRuns() {
    rememberedRuns = jdbc.queryForObject("SELECT count(*) FROM extract_run", Integer.class);
  }

  @Then("the extract answers 400 problem+json")
  public void badRequest() {
    assertThat(start.getResponse().getStatus()).isEqualTo(400);
    assertThat(start.getResponse().getContentType()).startsWith("application/problem+json");
  }

  @Then("no directory extract run was created")
  public void noRun() {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM extract_run", Integer.class))
        .isEqualTo(rememberedRuns);
  }

  // ---- AC-3 to AC-5: the file -----------------------------------------------------------------

  @Then("the file has exactly {int} record(s) of 100 bytes each and nothing else")
  public void exactRecords(int records) {
    assertThat(file).hasSize(records * Dirout.RECORD);
    assertThat(Dirout.records(file)).hasSize(records);
  }

  @Then("the file is empty")
  public void empty() {
    assertThat(file).isEmpty();
  }

  @Then("record {int} of the file is for the NPI {string}")
  public void recordNpi(int n, String npi) {
    assertThat(Dirout.npi(Dirout.records(file).get(n - 1))).isEqualTo(npi);
  }

  @Then("the file holds no record for the NPIs {string}")
  public void noRecordFor(String npis) {
    List<String> found = Dirout.records(file).stream().map(Dirout::npi).toList();
    assertThat(found).doesNotContainAnyElementsOf(Arrays.asList(npis.split(",")));
  }

  @Then("bytes {int} to {int} of record {int} are {string}")
  public void bytesAre(int from, int to, int n, String expected) {
    assertThat(Dirout.bytes(Dirout.records(file).get(n - 1), from, to)).isEqualTo(expected);
  }

  @Then("bytes {int} to {int} of record {int} are all spaces")
  public void bytesAreSpaces(int from, int to, int n) {
    assertThat(Dirout.bytes(Dirout.records(file).get(n - 1), from, to))
        .isEqualTo(" ".repeat(to - from + 1));
  }

  @Then("the 60-byte name of record {int} is {string} followed by spaces")
  public void name(int n, String name) {
    String field = Dirout.name(Dirout.records(file).get(n - 1));
    assertThat(field).hasSize(60);
    assertThat(field).isEqualTo(Dirout.padded(name, 60));
  }

  @Then("no record of the file contains {string}")
  public void doesNotContain(String text) {
    assertThat(new String(file, StandardCharsets.ISO_8859_1)).doesNotContain(text);
  }

  // ---- AC-6 -----------------------------------------------------------------------------------

  @Then("the two runs have different run ids")
  public void differentRuns() {
    assertThat(runIds).hasSize(2);
    assertThat(runIds.get(0)).isNotEqualTo(runIds.get(1));
  }

  private byte[] fileOf(int runIndex) throws Exception {
    MvcResult r = download(PemJwt.admin(), runIds.get(runIndex));
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    return r.getResponse().getContentAsByteArray();
  }

  @Then("the file of the second run is byte-identical to the file of the first run")
  public void identical() throws Exception {
    assertThat(fileOf(1)).isNotEmpty().isEqualTo(fileOf(0));
  }

  @Then("the file of the first run can still be downloaded")
  public void firstStillThere() throws Exception {
    assertThat(fileOf(0)).hasSize(2 * Dirout.RECORD);
  }

  // ---- AC-7 -----------------------------------------------------------------------------------

  @Given("the database rejects the write that completes a directory extract run")
  public void rejectCompletion() {
    jdbc.execute(
        "CREATE FUNCTION reject_completed_run() RETURNS trigger AS $$ BEGIN"
            + " RAISE EXCEPTION 'extract file rejected by the test database'; END; $$"
            + " LANGUAGE plpgsql");
    jdbc.execute(
        "CREATE TRIGGER reject_completed_run BEFORE UPDATE ON extract_run FOR EACH ROW"
            + " WHEN (NEW.status = 'COMPLETED') EXECUTE FUNCTION reject_completed_run()");
  }

  @Then("the run has a non-empty error text and an end time")
  public void failedRun() throws Exception {
    JsonNode run = body(start);
    assertThat(run.path("error").asText()).isNotBlank();
    assertThat(run.path("endedAt").asText()).isNotBlank();
  }

  @Then("downloading the file of that run answers 404")
  public void noFile() throws Exception {
    MvcResult r = download(PemJwt.admin(), runIds.get(runIds.size() - 1));
    assertThat(r.getResponse().getStatus()).isEqualTo(404);
    String stored =
        jdbc.queryForObject(
            "SELECT extract_file FROM extract_run WHERE public_id = ?",
            String.class,
            runIds.get(runIds.size() - 1));
    assertThat(stored).as("no partial file is stored").isNull();
  }

  // ---- AC-8 -----------------------------------------------------------------------------------

  private static RequestPostProcessor as(String role) {
    return switch (role) {
      case "ADMIN" -> PemJwt.admin();
      case "SYSTEM" -> PemJwt.system();
      case "ANALYST" -> PemJwt.analyst();
      case "SUPERVISOR" -> PemJwt.supervisor();
      case "PROVIDER" -> PemJwt.provider("1003000111");
      default -> null; // no token
    };
  }

  private RequestPostProcessor caller;

  @Given("the ADMIN has started an earlier directory extract for {string}")
  public void earlier(String date) throws Exception {
    earlierRun = started(post(PemJwt.admin(), startBody(date)));
  }

  @When("a caller with the role {string} starts the extract for {string}")
  public void callerStarts(String role, String date) throws Exception {
    caller = as(role);
    callerStart = post(caller, startBody(date));
  }

  @When("the same caller downloads the file of the earlier run")
  public void callerDownloads() throws Exception {
    callerDownload = download(caller, earlierRun);
  }

  @Then("the start answers {int} and the download answers {int}")
  public void statuses(int startStatus, int downloadStatus) {
    assertThat(callerStart.getResponse().getStatus()).as("start").isEqualTo(startStatus);
    assertThat(callerDownload.getResponse().getStatus()).as("download").isEqualTo(downloadStatus);
  }
}
