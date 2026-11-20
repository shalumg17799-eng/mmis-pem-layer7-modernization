package gov.medicaid.pem.enrollment.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.enrollment.AppInputFile;
import gov.medicaid.pem.enrollment.ContractFile;
import gov.medicaid.pem.enrollment.DbRejections;
import gov.medicaid.pem.enrollment.IntakeApi;
import gov.medicaid.pem.enrollment.IntakeIntegrationTest.Captured;
import gov.medicaid.pem.testsupport.PemJwt;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Steps of MAM-56 (the APPINPUT intake run, the APPERROR file) and MAM-57 (the run result: counts,
 * completion, failure, re-runs). The records are the synthetic 200-byte records of {@link
 * AppInputFile}; the file of a scenario is the records given, one per line. A run goes on in a
 * background thread of the service, so every wait is bounded, and the scenario ends only when no
 * job is still running.
 */
public class IntakeRunSteps {

  private static final String RESERVED_TEXT = "RESERVED BYTES 131 TO 200 MUST NOT BE READ";
  private static final Set<String> EVENT_DATA_KEYS =
      Set.of(
          "applicationId",
          "npi",
          "medicaidId",
          "providerType",
          "taxonomy",
          "individual",
          "organization");

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private final List<String> records = new ArrayList<>();
  private final List<String> runIds = new ArrayList<>();
  private DbRejections rejections;
  private Captured log;
  private MvcResult answer;
  private String errorFile;

  @Before("@MAM-56 or @MAM-57")
  public void begin() {
    rejections = new DbRejections(jdbc);
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    log = new Captured();
    log.setContext(root.getLoggerContext());
    log.start();
    root.addAppender(log);
  }

  @After("@MAM-56 or @MAM-57")
  public void end() {
    ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(log);
    log.stop();
    Awaitility.await("no intake job is still running")
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () ->
                jdbc.queryForObject(
                        "SELECT count(*) FROM batch_job_execution"
                            + " WHERE status IN ('STARTING', 'STARTED')",
                        Integer.class)
                    == 0);
    rejections.liftAll();
    wipe();
  }

  private void wipe() {
    jdbc.update("DELETE FROM application_error");
    jdbc.update("DELETE FROM application");
    jdbc.update("DELETE FROM intake_run");
    jdbc.update("DELETE FROM outbox_event");
  }

  // ---- the file -------------------------------------------------------------------------------

  @Given("no intake runs or applications exist")
  public void nothingExists() {
    wipe();
  }

  @Given("an APPINPUT file of valid records for NPIs {string}")
  public void validFile(String npis) {
    records.clear();
    for (String npi : npis.split(",")) {
      records.add(AppInputFile.valid(npi.trim()));
    }
  }

  @Given("a valid APPINPUT record for NPI {string}")
  public void validRecord(String npi) {
    records.add(AppInputFile.valid(npi));
  }

  @Given("an APPINPUT record with a blank NPI")
  public void blankNpi() {
    records.add(AppInputFile.noNpi());
  }

  @Given("an APPINPUT record for NPI {string} with type {string}")
  public void withType(String npi, String type) {
    records.add(AppInputFile.ofType(npi, type));
  }

  @Given("an APPINPUT record for NPI {string} with an empty taxonomy")
  public void withoutTaxonomy(String npi) {
    records.add(AppInputFile.noTaxonomy(npi));
  }

  @Given("an APPINPUT record for NPI {string} with type {string} and an empty taxonomy")
  public void withTypeWithoutTaxonomy(String npi, String type) {
    records.add(
        AppInputFile.individual(
            npi, "M00000" + npi.substring(7), type, "", "RIVERA", "MARIA", "MD12345", "TX"));
  }

  @Given(
      "an APPINPUT individual record for NPI {string} with the legacy bytes 32-50 and the reserved bytes 131-200 filled")
  public void individualWithLegacyBytes(String npi) {
    String r = AppInputFile.valid(npi);
    r = AppInputFile.with(r, 32, "HAP");
    r = AppInputFile.with(r, 35, "20260115");
    r = AppInputFile.with(r, 43, "20310115");
    r = AppInputFile.with(r, 131, RESERVED_TEXT);
    records.add(r);
  }

  @Given(
      "an APPINPUT group record for NPI {string} with legal name {string}, no DBA name and owner NPI {string}")
  public void group(String npi, String legalName, String owner) {
    records.add(
        AppInputFile.organization(
            npi, "M00000" + npi.substring(7), "GR", "207Q00000X", legalName, "", owner));
  }

  @Given("the third record is cut to {int} bytes")
  public void cutThird(int bytes) {
    records.set(2, AppInputFile.cut(records.get(2), bytes));
  }

  @Given("the database rejects the storage of an application for NPI {string}")
  public void databaseRejects(String npi) {
    rejections.reject("reject_app_" + npi, "INSERT", "application", "NEW.npi = '" + npi + "'");
  }

  @When("the database accepts the storage again")
  public void databaseAccepts() {
    rejections.liftAll();
  }

  // ---- starting and reading runs ---------------------------------------------------------------

  private static RequestPostProcessor token(String role) {
    return switch (role) {
      case "ADMIN" -> PemJwt.admin();
      case "SYSTEM" -> PemJwt.system();
      case "ANALYST" -> PemJwt.analyst();
      case "SUPERVISOR" -> PemJwt.supervisor();
      case "PROVIDER" -> PemJwt.provider("1003000111");
      default -> null;
    };
  }

  private byte[] file() {
    return AppInputFile.lines(records.toArray(String[]::new));
  }

  private void start(RequestPostProcessor who, String date, String batchId) throws Exception {
    answer = IntakeApi.start(mvc, who, file(), date, batchId);
    if (answer.getResponse().getStatus() == 202) {
      runIds.add(json.readTree(answer.getResponse().getContentAsString()).path("runId").asText());
    }
  }

  private void runToEnd(String date) throws Exception {
    start(PemJwt.admin(), date, null);
    assertThat(answer.getResponse().getStatus())
        .as(answer.getResponse().getContentAsString())
        .isEqualTo(202);
    IntakeApi.await(mvc, json, lastRun());
  }

  private String lastRun() {
    return runIds.get(runIds.size() - 1);
  }

  @When("an ADMIN starts an intake run with business date {string} and batch id {string}")
  public void startWithBatch(String date, String batchId) throws Exception {
    start(PemJwt.admin(), date, batchId);
  }

  @When("an ADMIN starts an intake run with business date {string} and no batch id")
  public void startWithoutBatch(String date) throws Exception {
    start(PemJwt.admin(), date, null);
  }

  @When("an ADMIN starts an intake run with business date {string} and a batch id of blanks")
  public void startWithBlankBatch(String date) throws Exception {
    start(PemJwt.admin(), date, "   ");
  }

  @Given("an ADMIN has run the file for business date {string}")
  public void adminHasRun(String date) throws Exception {
    runToEnd(date);
  }

  @When("an ADMIN runs the file for business date {string}")
  public void adminRuns(String date) throws Exception {
    runToEnd(date);
  }

  @When("an ADMIN runs the same file again for business date {string}")
  public void adminRunsAgain(String date) throws Exception {
    runToEnd(date);
  }

  @When("{string} starts an intake run for business date {string}")
  public void roleStarts(String role, String date) throws Exception {
    start(token(role), date, null);
  }

  @When("an ADMIN starts an intake run without a business date")
  public void withoutDate() throws Exception {
    start(PemJwt.admin(), null, null);
  }

  @When("an ADMIN starts an intake run without a file")
  public void withoutFile() throws Exception {
    answer = IntakeApi.start(mvc, PemJwt.admin(), null, "2026-03-31", null);
  }

  @When("an ADMIN downloads the error file of the run")
  public void adminDownloads() throws Exception {
    answer = IntakeApi.errorFile(mvc, lastRun(), PemJwt.admin());
    errorFile =
        answer.getResponse().getContentAsString(java.nio.charset.StandardCharsets.ISO_8859_1);
  }

  @When("{string} downloads the error file of the run")
  public void roleDownloads(String role) throws Exception {
    answer = IntakeApi.errorFile(mvc, lastRun(), token(role));
  }

  @When("an ADMIN downloads the error file of the run {string}")
  public void adminDownloadsOf(String runId) throws Exception {
    answer = IntakeApi.errorFile(mvc, runId, PemJwt.admin());
  }

  @When("{string} reads the run")
  public void roleReads(String role) throws Exception {
    answer = IntakeApi.read(mvc, lastRun(), token(role));
  }

  @When("an ADMIN reads the run {string}")
  public void adminReadsRun(String runId) throws Exception {
    answer = IntakeApi.read(mvc, runId, PemJwt.admin());
  }

  // ---- the answer -------------------------------------------------------------------------------

  @Then("the intake answer is {int}")
  public void answerIs(int status) throws Exception {
    assertThat(answer.getResponse().getStatus())
        .as("answer %s", answer.getResponse().getContentAsString())
        .isEqualTo(status);
  }

  @Then("the intake answer is problem+json when it is not {int}")
  public void problemUnless(int ok) {
    if (answer.getResponse().getStatus() != ok) {
      assertThat(answer.getResponse().getContentType()).startsWith("application/problem+json");
    }
  }

  @Then(
      "the intake answer is 202 with a run id, job {string}, business date {string} and status RUNNING or COMPLETED")
  public void accepted(String job, String date) throws Exception {
    assertThat(answer.getResponse().getStatus())
        .as(answer.getResponse().getContentAsString())
        .isEqualTo(202);
    JsonNode run = json.readTree(answer.getResponse().getContentAsString());
    assertThat(UUID.fromString(run.path("runId").asText())).isNotNull();
    assertThat(run.path("job").asText()).isEqualTo(job);
    assertThat(run.path("businessDate").asText()).isEqualTo(date);
    assertThat(run.path("status").asText()).isIn("RUNNING", "COMPLETED");
    assertThat(ContractFile.violations("BatchRun", run)).isEmpty();
  }

  @Then("reading the run again shows business date {string}")
  public void readAgain(String date) throws Exception {
    assertThat(IntakeApi.run(mvc, json, lastRun()).path("businessDate").asText()).isEqualTo(date);
  }

  @Then("the log contains the line {string}")
  public void logContains(String line) {
    assertThat(log.messages()).contains(line);
  }

  @Then("the number of intake runs is {int}")
  public void runsExist(int count) {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM intake_run", Integer.class))
        .isEqualTo(count);
  }

  // ---- the applications -------------------------------------------------------------------------

  private List<JsonNode> applicationsAsAnalyst(String query) throws Exception {
    MvcResult r =
        mvc.perform(get("/api/v1/applications?size=200" + query).with(PemJwt.analyst()))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    List<JsonNode> items = new ArrayList<>();
    json.readTree(r.getResponse().getContentAsString()).path("items").forEach(items::add);
    return items;
  }

  private static String text(String cell) {
    return cell == null ? "" : cell;
  }

  private static String errorsOf(JsonNode application) {
    List<String> errors = new ArrayList<>();
    application
        .path("errors")
        .forEach(e -> errors.add(e.path("code").asText() + "/" + e.path("field").asText()));
    return String.join(",", errors);
  }

  @Then(
      "the ANALYST sees one VALIDATED application for NPI {string} with source {string}, Medicaid ID {string}, type {string}, taxonomy {string} and the individual RIVERA MARIA MD12345 TX")
  public void individualShown(
      String npi, String source, String medicaidId, String type, String taxonomy) throws Exception {
    List<JsonNode> items = applicationsAsAnalyst("&npi=" + npi);
    assertThat(items).hasSize(1);
    JsonNode a = items.get(0);
    assertThat(a.path("status").asText()).isEqualTo("VALIDATED");
    assertThat(a.path("source").asText()).isEqualTo(source);
    assertThat(a.path("medicaidId").asText()).isEqualTo(medicaidId);
    assertThat(a.path("providerType").asText()).isEqualTo(type);
    assertThat(a.path("taxonomy").asText()).isEqualTo(taxonomy);
    JsonNode ind = a.path("individual");
    assertThat(ind.path("lastName").asText()).isEqualTo("RIVERA");
    assertThat(ind.path("firstName").asText()).isEqualTo("MARIA");
    assertThat(ind.path("licenseNo").asText()).isEqualTo("MD12345");
    assertThat(ind.path("licenseState").asText()).isEqualTo("TX");
    assertThat(a.has("organization")).isFalse();
  }

  @Then(
      "the ANALYST sees the application for NPI {string} with legal name {string}, no DBA name and owner NPI {string}")
  public void organizationShown(String npi, String legalName, String owner) throws Exception {
    List<JsonNode> items = applicationsAsAnalyst("&npi=" + npi);
    assertThat(items).hasSize(1);
    JsonNode org = items.get(0).path("organization");
    assertThat(org.path("legalName").asText()).isEqualTo(legalName);
    assertThat(org.has("dbaName")).isFalse();
    assertThat(org.path("ownerNpi").asText()).isEqualTo(owner);
    assertThat(items.get(0).has("individual")).isFalse();
  }

  @Then(
      "neither the applications nor their ApplicationValidated events carry the reserved bytes or the tier, status, flag and dates of the record")
  public void nothingLegacyIsCarried() throws Exception {
    List<JsonNode> items = applicationsAsAnalyst("");
    assertThat(items).hasSize(2);
    for (JsonNode a : items) {
      assertThat(ContractFile.violations("Application", a)).isEmpty();
      String text = a.toString();
      assertThat(text)
          .doesNotContain(RESERVED_TEXT)
          .doesNotContain("20260115")
          .doesNotContain("20310115");
    }
    List<String> payloads =
        jdbc.queryForList(
            "SELECT payload::text FROM outbox_event WHERE type = 'ApplicationValidated'",
            String.class);
    assertThat(payloads).hasSize(2);
    for (String payload : payloads) {
      assertThat(payload)
          .doesNotContain(RESERVED_TEXT)
          .doesNotContain("20260115")
          .doesNotContain("20310115");
      List<String> keys = new ArrayList<>();
      json.readTree(payload).path("data").fieldNames().forEachRemaining(keys::add);
      assertThat(keys).isSubsetOf(EVENT_DATA_KEYS);
    }
  }

  @Then("the ANALYST sees these applications:")
  public void applicationsShown(DataTable table) throws Exception {
    List<String> expected = new ArrayList<>();
    for (Map<String, String> row : table.asMaps()) {
      expected.add(
          text(row.get("npi"))
              + "|"
              + text(row.get("status"))
              + "|"
              + text(row.get("source"))
              + "|"
              + text(row.get("errors")));
    }
    List<String> actual = new ArrayList<>();
    for (JsonNode a : applicationsAsAnalyst("")) {
      actual.add(
          a.path("npi").asText()
              + "|"
              + a.path("status").asText()
              + "|"
              + a.path("source").asText()
              + "|"
              + errorsOf(a));
    }
    assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
  }

  @Then("the applications were stored in the file order of NPIs {string}")
  public void fileOrder(String npis) {
    List<String> stored =
        jdbc.queryForList("SELECT trim(npi) FROM application ORDER BY id", String.class);
    assertThat(stored).containsExactly(npis.split(","));
  }

  @Then("the application for NPI {string} is still {string}")
  public void stillStatus(String npi, String status) {
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM application WHERE npi = ? ORDER BY id LIMIT 1",
                String.class,
                npi))
        .isEqualTo(status);
  }

  @Then("exactly {int} events are published")
  public void eventTotal(int count) {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event", Integer.class))
        .isEqualTo(count);
  }

  // ---- the run result -------------------------------------------------------------------------

  private JsonNode run(int number) throws Exception {
    return IntakeApi.run(mvc, json, runIds.get(number - 1));
  }

  private void assertCounts(JsonNode run, int read, int written, int errored, int skipped) {
    JsonNode counts = run.path("counts");
    assertThat(counts.path("read").asInt()).as("read of %s", run).isEqualTo(read);
    assertThat(counts.path("written").asInt()).as("written of %s", run).isEqualTo(written);
    assertThat(counts.path("errored").asInt()).as("errored of %s", run).isEqualTo(errored);
    assertThat(counts.path("skipped").asInt()).as("skipped of %s", run).isEqualTo(skipped);
  }

  @Then(
      "run {int} shows status {string} with end time set, error null and counts read {int}, written {int}, errored {int}, skipped {int}")
  public void runShows(int number, String status, int read, int written, int errored, int skipped)
      throws Exception {
    JsonNode run = run(number);
    assertThat(run.path("status").asText()).as("run %s", run).isEqualTo(status);
    assertThat(run.path("endedAt").asText()).isNotBlank();
    assertThat(run.path("error").isMissingNode() || run.path("error").isNull())
        .as("error of %s", run)
        .isTrue();
    assertCounts(run, read, written, errored, skipped);
  }

  @Then("the counts of run {int} add up: read equals written plus errored plus skipped")
  public void countsAddUp(int number) throws Exception {
    JsonNode counts = run(number).path("counts");
    assertThat(counts.path("read").asInt())
        .isEqualTo(
            counts.path("written").asInt()
                + counts.path("errored").asInt()
                + counts.path("skipped").asInt());
  }

  @Then("run {int} shows counts read {int}, written {int}, errored {int}, skipped {int}")
  public void runCounts(int number, int read, int written, int errored, int skipped)
      throws Exception {
    assertCounts(run(number), read, written, errored, skipped);
  }

  @Then("the run's counts.read is {int}")
  public void countsRead(int read) throws Exception {
    assertThat(IntakeApi.run(mvc, json, lastRun()).path("counts").path("read").asInt())
        .isEqualTo(read);
  }

  @Then("run {int} ends FAILED with an error naming record {int} and an end time")
  public void failedNamingRecord(int number, int record) throws Exception {
    JsonNode run = run(number);
    assertThat(run.path("status").asText()).as("run %s", run).isEqualTo("FAILED");
    assertThat(run.path("error").asText()).contains("record " + record);
    assertThat(run.path("endedAt").asText()).isNotBlank();
  }

  @Then("run {int} ends FAILED with an error naming record {int} and NPI {string} and an end time")
  public void failedNamingRecordAndNpi(int number, int record, String npi) throws Exception {
    JsonNode run = run(number);
    assertThat(run.path("status").asText()).as("run %s", run).isEqualTo("FAILED");
    assertThat(run.path("error").asText()).contains("record " + record).contains(npi);
    assertThat(run.path("endedAt").asText()).isNotBlank();
  }

  // ---- the error file -------------------------------------------------------------------------

  @Then("the error file has exactly these rows in input order:")
  public void errorRows(DataTable table) {
    List<String> expected = new ArrayList<>();
    for (Map<String, String> row : table.asMaps()) {
      expected.add(AppInputFile.errorRow(text(row.get("npi")), text(row.get("type"))));
    }
    assertThat(AppInputFile.rows(errorFile)).containsExactlyElementsOf(expected);
  }

  @Then("the error file has {int} rows")
  public void errorRowCount(int count) {
    assertThat(AppInputFile.rows(errorFile)).hasSize(count);
  }

  @Then("the error file is empty")
  public void errorFileEmpty() {
    assertThat(errorFile).isEmpty();
  }

  @Then(
      "every row of the error file is 250 characters in the layout \"NPI TYPE:tt ERR:VALIDATION-FAILED\" and spaces")
  public void errorRowLayout() {
    List<String> rows = AppInputFile.rows(errorFile);
    assertThat(rows).isNotEmpty();
    for (String row : rows) {
      assertThat(row).hasSize(250);
      assertThat(row.substring(10, 16)).isEqualTo(" TYPE:");
      assertThat(row.substring(18, 40)).isEqualTo(" ERR:VALIDATION-FAILED");
      assertThat(row.substring(40)).isEqualTo(" ".repeat(210));
    }
  }
}
