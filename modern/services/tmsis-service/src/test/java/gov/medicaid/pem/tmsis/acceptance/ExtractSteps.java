package gov.medicaid.pem.tmsis.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.testsupport.PemJwt;
import gov.medicaid.pem.tmsis.KafkaEvents;
import gov.medicaid.pem.tmsis.KafkaEvents.Message;
import gov.medicaid.pem.tmsis.LogCapture;
import gov.medicaid.pem.tmsis.ServiceRestart;
import gov.medicaid.pem.tmsis.TestClock;
import gov.medicaid.pem.tmsis.TestProviderService;
import gov.medicaid.pem.tmsis.TestProviderService.Fault;
import gov.medicaid.pem.tmsis.TestProviderService.Prov;
import gov.medicaid.pem.tmsis.TmsisApi;
import gov.medicaid.pem.tmsis.Tmsisout;
import gov.medicaid.pem.tmsis.application.ExtractRuns;
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
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Steps for MAM-71 (the TMSISOUT file), MAM-75 (runs, archive, ExtractProduced, access) and MAM-78
 * (future-enrolment warning). The tmsis-service of the scenarios is the real application with
 * Postgres and Kafka (Testcontainers) and a fake provider-service; a run is asynchronous, so every
 * step that needs its end waits for it with a time limit.
 */
public class ExtractSteps {

  private static boolean warm;

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  @Autowired private ScheduledTaskHolder scheduler;
  @Autowired private ExtractRuns runs;
  @Autowired private ConfigurableApplicationContext context;

  private TmsisApi api;
  private LogCapture log;
  private MvcResult startResult;
  private final List<String> runIds = new ArrayList<>();
  private final List<byte[]> files = new ArrayList<>();
  private byte[] file;
  private JsonNode listedRun;
  private long elapsedMillis;
  private int rememberedRuns;
  private String earlierRunId;
  private RequestPostProcessor caller;
  private MvcResult callerStart;
  private MvcResult callerList;
  private MvcResult callerDownload;

  // ---- hooks ------------------------------------------------------------------------------------

  @Before("@MAM-71 or @MAM-75 or @MAM-78")
  public void reset() {
    api = new TmsisApi(mvc, json);
    TestProviderService.reset();
    TestClock.reset();
    if (!warm) {
      warm = true;
      try {
        TestProviderService.holds(Prov.active("1003000001"));
        api.runToEnd("2026-01-15");
      } catch (Throwable e) {
        // the scenarios show what is wrong; the warm-up proves nothing
      } finally {
        TestProviderService.reset();
        runsHaveEnded();
      }
    }
    jdbc.update("DELETE FROM outbox_event");
    jdbc.update("DELETE FROM extract_archive");
    jdbc.update("DELETE FROM extract_row");
    jdbc.update("DELETE FROM extract_run");
    log = LogCapture.open();
  }

  @After("@MAM-71 or @MAM-75 or @MAM-78")
  public void cleanUp() {
    log.close();
    runsHaveEnded();
    TestProviderService.reset();
    TestClock.reset();
  }

  private void runsHaveEnded() {
    try {
      Awaitility.await()
          .atMost(Duration.ofSeconds(30))
          .pollInterval(Duration.ofMillis(100))
          .ignoreExceptions()
          .until(
              () ->
                  jdbc.queryForObject(
                          "SELECT count(*) FROM extract_run WHERE status = 'RUNNING'",
                          Integer.class)
                      == 0);
    } catch (RuntimeException e) {
      jdbc.update("UPDATE extract_run SET status = 'FAILED' WHERE status = 'RUNNING'");
    }
  }

  // ---- Given ------------------------------------------------------------------------------------

  @Given("provider-service holds these providers:")
  public void holdsProviders(DataTable table) {
    List<Prov> providers = new ArrayList<>();
    for (Map<String, String> row : table.asMaps()) {
      providers.add(
          new Prov(
              row.get("npi"), row.get("medicaidId"), row.get("status"), row.get("effectiveDate")));
    }
    TestProviderService.holds(providers);
  }

  @Given("provider-service holds ACTIVE providers with the NPIs {string}")
  public void holdsActive(String npis) {
    TestProviderService.holds(
        Arrays.stream(npis.split(",")).map(String::trim).map(Prov::active).toList());
  }

  @Given("provider-service holds {int} ACTIVE providers with the NPIs from {string} to {string}")
  public void holdsMany(int count, String from, String to) {
    List<Prov> all = new ArrayList<>();
    long first = Long.parseLong(from);
    for (int i = 0; i < count; i++) {
      all.add(Prov.active(Long.toString(first + i)));
    }
    assertThat(all.get(all.size() - 1).npi()).isEqualTo(to);
    TestProviderService.holds(all);
  }

  @Given("provider-service holds no providers")
  public void holdsNone() {
    TestProviderService.holds();
  }

  @Given("provider-service ignores the status filter of the search")
  public void ignoresStatusFilter() {
    TestProviderService.ignoreStatusFilter();
  }

  @Given("provider-service answers every request with a server error")
  public void serverError() {
    TestProviderService.providerFails(Fault.SERVER_ERROR);
  }

  @Given("provider-service does not answer in time")
  public void doesNotAnswer() {
    TestProviderService.providerFails(Fault.SLOW);
  }

  @Given("the system clock shows {string}")
  public void clockShows(String instant) {
    TestClock.set(instant);
  }

  @Given("provider-service also holds the PENDING provider {string}")
  public void alsoHoldsPending(String npi) {
    TestProviderService.add(Prov.of(npi, "P"));
  }

  @Given("provider-service terminates the provider {string} after its first answer")
  public void terminatesAfterTheFirstAnswer(String npi) {
    TestProviderService.afterSearch(1, () -> TestProviderService.changeStatus(npi, "T"));
  }

  @Given("provider-service approves the provider {string} after its first answer")
  public void approvesAfterTheFirstAnswer(String npi) {
    TestProviderService.afterSearch(1, () -> TestProviderService.changeStatus(npi, "A"));
  }

  @Given(
      "provider-service ignores the key of the search, as a provider-service older than 1.2.0 does")
  public void ignoresTheKey() {
    TestProviderService.answerIgnoringTheKey();
  }

  @Given("a run for {string} was RUNNING when tmsis-service stopped")
  public void runWasRunningWhenTheServiceStopped(String date) {
    runIds.add(ServiceRestart.crashedDuringRun(jdbc, runs, date, true).toString());
  }

  @Given("the archive is empty")
  public void archiveIsEmpty() {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM extract_archive", Integer.class)).isZero();
  }

  @Given("the ADMIN has run the extract for {string}")
  public void adminHasRun(String date) throws Exception {
    earlierRunId = api.runToEnd(date).path("runId").asText();
  }

  @Given("the number of extract runs is remembered")
  public void rememberRuns() {
    rememberedRuns = jdbc.queryForObject("SELECT count(*) FROM extract_run", Integer.class);
  }

  // ---- When -------------------------------------------------------------------------------------

  @When("the ADMIN starts the extract for {string}")
  public void adminStarts(String date) throws Exception {
    startResult = api.postBody(PemJwt.admin(), TmsisApi.body(date));
    if (startResult.getResponse().getStatus() == 202) {
      runIds.add(api.read(startResult).path("runId").asText());
    }
  }

  @When("the ADMIN starts the extract for {string} and waits for the run to end")
  public void adminStartsAndWaits(String date) throws Exception {
    long begin = System.nanoTime();
    adminStarts(date);
    listedRun = api.await(lastRunId());
    elapsedMillis = Duration.ofNanos(System.nanoTime() - begin).toMillis();
  }

  @When("the ADMIN runs the extract for {string} and waits for it")
  public void adminRunsAndWaits(String date) throws Exception {
    JsonNode run = api.runToEnd(date);
    runIds.add(run.path("runId").asText());
    files.add(api.file(run.path("runId").asText()));
    listedRun = run;
  }

  @When("the ADMIN runs the extract for {string} and downloads the file")
  public void adminRunsAndDownloads(String date) throws Exception {
    adminStartsAndWaits(date);
    adminDownloads();
  }

  @When("the ADMIN downloads the file of the run")
  public void adminDownloads() throws Exception {
    file = api.file(lastRunId());
  }

  @When("the monthly schedule fires")
  public void scheduleFires() throws Exception {
    CronTask task =
        scheduler.getScheduledTasks().stream()
            .map(ScheduledTask::getTask)
            .filter(CronTask.class::isInstance)
            .map(CronTask.class::cast)
            .filter(t -> String.valueOf(t.getRunnable()).endsWith("TmsisSchedule.monthly"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("TmsisSchedule.monthly() is not scheduled"));
    task.getRunnable().run();
    Awaitility.await("the scheduled task has started a run")
        .atMost(Duration.ofSeconds(20))
        .pollInterval(Duration.ofMillis(100))
        .until(() -> api.list(0, 20).path("total").asInt() >= 1);
    String runId = api.list(0, 20).path("items").get(0).path("runId").asText();
    runIds.add(runId);
    listedRun = api.await(runId);
  }

  @When("tmsis-service starts again")
  public void serviceStartsAgain() throws Exception {
    ServiceRestart.startsAgain(context);
    String runId = lastRunId();
    try {
      Awaitility.await("the run " + runId + " has left RUNNING")
          .atMost(Duration.ofSeconds(8))
          .pollInterval(Duration.ofMillis(100))
          .until(() -> !"RUNNING".equals(api.listed(runId).path("status").asText()));
    } catch (ConditionTimeoutException e) {
      // the steps that follow show the status
    }
    listedRun = api.listed(runId);
  }

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

  @When("a caller with the role {string} starts the extract for {string}")
  public void callerStarts(String role, String date) throws Exception {
    caller = as(role);
    callerStart = api.postBody(caller, TmsisApi.body(date));
    if (callerStart.getResponse().getStatus() == 202) {
      api.await(api.read(callerStart).path("runId").asText());
    }
  }

  @When("the same caller lists the runs and downloads the file of the earlier run")
  public void callerListsAndDownloads() throws Exception {
    callerList = api.getPath(caller, TmsisApi.BASE);
    callerDownload = api.getPath(caller, TmsisApi.BASE + "/" + earlierRunId + "/file");
  }

  @When("the ADMIN starts the extract with the body {string}")
  public void adminStartsWithBody(String body) throws Exception {
    startResult = api.postBody(PemJwt.admin(), body);
  }

  // ---- Then: the file ---------------------------------------------------------------------------

  @Then("the file has exactly {int} record(s) of 120 bytes each and nothing else")
  public void exactRecords(int records) {
    assertThat(file).hasSize(records * Tmsisout.RECORD);
    assertThat(Tmsisout.records(file)).hasSize(records);
  }

  @Then("the file is empty")
  public void fileIsEmpty() {
    assertThat(file).isEmpty();
  }

  @Then("record {int} of the file is for the NPI {string}")
  public void recordNpi(int n, String npi) {
    assertThat(Tmsisout.npi(Tmsisout.records(file).get(n - 1))).isEqualTo(npi);
  }

  @Then("the file holds no record for the NPIs {string}")
  public void noRecordFor(String npis) {
    assertThat(Tmsisout.npis(file)).doesNotContainAnyElementsOf(Arrays.asList(npis.split(",")));
  }

  @Then("bytes {int} to {int} of record {int} are {string}")
  public void bytesAre(int from, int to, int n, String expected) {
    assertThat(Tmsisout.bytes(Tmsisout.records(file).get(n - 1), from, to)).isEqualTo(expected);
  }

  @Then("bytes {int} to {int} of record {int} are all spaces")
  public void bytesAreSpaces(int from, int to, int n) {
    assertThat(Tmsisout.bytes(Tmsisout.records(file).get(n - 1), from, to))
        .isEqualTo(" ".repeat(to - from + 1));
  }

  @Then("every record of the file has the period {string}")
  public void everyRecordPeriod(String period) {
    assertThat(Tmsisout.records(file)).isNotEmpty();
    assertThat(Tmsisout.records(file))
        .allSatisfy(r -> assertThat(Tmsisout.period(r)).isEqualTo(period));
  }

  @Then("no record of the file has the period {string}")
  public void noRecordPeriod(String period) {
    assertThat(Tmsisout.records(file))
        .noneSatisfy(r -> assertThat(Tmsisout.period(r)).isEqualTo(period));
  }

  @Then("the NPIs of the file are ascending and each appears once")
  public void ascendingNpis() {
    assertThat(Tmsisout.npis(file)).isSorted().doesNotHaveDuplicates();
  }

  // ---- Then: how the providers were read (MAM-71 AC-5, AC-6) ------------------------------------

  @Then(
      "provider-service was asked in {int} requests of the size {int}, the first without a key and the others after the NPIs {string}")
  public void keysAsked(int requests, int size, String keys) {
    var searches = TestProviderService.searches();
    assertThat(searches).hasSize(requests);
    List<String> expected = new ArrayList<>();
    expected.add(null);
    expected.addAll(Arrays.asList(keys.split(",")));
    assertThat(searches.stream().map(r -> r.query().get("afterNpi")).toList())
        .containsExactlyElementsOf(expected);
    assertThat(searches).allSatisfy(r -> assertThat(r.query()).containsEntry("size", "" + size));
    assertThat(searches)
        .allSatisfy(r -> assertThat(r.query().getOrDefault("page", "0")).isEqualTo("0"));
  }

  @Then(
      "provider-service received {int} searches with the status {string}, page after page, authenticated with the SYSTEM service token")
  public void searchesReceived(int count, String status) {
    var searches = TestProviderService.searches();
    assertThat(searches).hasSize(count);
    assertThat(searches)
        .allSatisfy(
            r -> {
              assertThat(r.method()).isEqualTo("GET");
              assertThat(r.path()).isEqualTo("/api/v1/providers");
              assertThat(r.query()).containsEntry("status", status);
              assertThat(r.authorization()).isEqualTo("Bearer " + TestProviderService.SYSTEM_TOKEN);
            });
  }

  @Then("tmsis-service holds no provider table of its own")
  public void noProviderTable() {
    assertThat(
            jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class))
        .contains("extract_run", "extract_row", "extract_archive")
        .doesNotContain("provider", "provider_owner", "screening", "sanction");
  }

  // ---- Then: the run (MAM-75) -------------------------------------------------------------------

  private String lastRunId() {
    return runIds.get(runIds.size() - 1);
  }

  @Then("the start answers 202 with a run for the job {string} and business date {string}")
  public void startAnswer(String job, String date) throws Exception {
    assertThat(startResult.getResponse().getStatus())
        .as(startResult.getResponse().getContentAsString())
        .isEqualTo(202);
    JsonNode run = api.read(startResult);
    assertThat(run.path("job").asText()).isEqualTo(job);
    assertThat(run.path("businessDate").asText()).isEqualTo(date);
    assertThat(run.path("runId").asText()).isNotBlank();
  }

  @Then("the status of the answer is RUNNING or COMPLETED")
  public void answerStatus() throws Exception {
    assertThat(api.read(startResult).path("status").asText()).isIn("RUNNING", "COMPLETED");
  }

  @Then("once the run has ended the list of runs shows that run id")
  public void listShowsTheRun() throws Exception {
    api.await(lastRunId());
    List<String> ids = new ArrayList<>();
    api.list(0, 20).path("items").forEach(i -> ids.add(i.path("runId").asText()));
    assertThat(ids).contains(lastRunId());
  }

  @Then("the run is listed as {string} with an end time")
  public void listedAs(String status) {
    assertThat(listedRun.path("status").asText()).as(listedRun.toString()).isEqualTo(status);
    assertThat(listedRun.path("endedAt").asText()).isNotBlank();
  }

  @Then("the run is listed as {string} with an end time and a non-empty error text")
  public void listedWithError(String status) {
    listedAs(status);
    assertThat(listedRun.path("error").asText()).isNotBlank();
  }

  @Then("the error text of the run is {string}")
  public void errorTextIs(String text) {
    assertThat(listedRun.path("error").asText()).isEqualTo(text);
  }

  @Then("the file holds a record for the NPIs {string}")
  public void fileHoldsRecordsFor(String npis) {
    assertThat(Tmsisout.npis(file)).containsAll(Arrays.asList(npis.split(",")));
  }

  @Then("the run counts {int} rows, as many as the records of its file")
  public void rowsCounted(int rows) {
    assertThat(listedRun.path("counts").path("rows").asInt()).isEqualTo(rows);
    assertThat(Tmsisout.records(file)).hasSize(rows);
  }

  @Then("the run shows the business date {string}")
  public void showsDate(String date) {
    assertThat(listedRun.path("businessDate").asText()).isEqualTo(date);
  }

  @Then("the three runs show the archive generations {string}")
  public void generations(String expected) throws Exception {
    List<String> found = new ArrayList<>();
    for (String id : runIds) {
      found.add(String.valueOf(api.listed(id).path("counts").path("archiveGeneration").asInt()));
    }
    assertThat(String.join(",", found)).isEqualTo(expected);
  }

  @Then("the file of every earlier run can still be downloaded unchanged after the later runs")
  public void earlierFilesUnchanged() throws Exception {
    for (int i = 0; i < runIds.size() - 1; i++) {
      assertThat(api.file(runIds.get(i))).isEqualTo(files.get(i));
    }
  }

  @Then("the third file is byte-identical to the second")
  public void thirdEqualsSecond() throws Exception {
    assertThat(files).hasSize(3);
    assertThat(api.file(runIds.get(2))).isNotEmpty().isEqualTo(files.get(1));
    assertThat(files.get(2)).isEqualTo(files.get(1));
  }

  @Then(
      "exactly one ExtractProduced event is published on {string} with source {string}, the run id as its key, business date {string}, period {string} and row count {int}")
  public void eventPublished(String topic, String source, String date, String period, int rows) {
    assertThat(topic).isEqualTo(KafkaEvents.TOPIC);
    List<Message> messages =
        KafkaEvents.extractProduced(lastRunId(), Duration.ofSeconds(40), Duration.ofSeconds(3));
    assertThat(messages).hasSize(1);
    Message m = messages.get(0);
    assertThat(m.key()).isEqualTo(lastRunId());
    assertThat(m.event().path("source").asText()).isEqualTo(source);
    assertThat(m.event().path("subject").asText()).isEqualTo(lastRunId());
    assertThat(m.event().path("data").path("runId").asText()).isEqualTo(lastRunId());
    assertThat(m.event().path("data").path("businessDate").asText()).isEqualTo(date);
    assertThat(m.event().path("data").path("periodYyddd").asText()).isEqualTo(period);
    assertThat(m.event().path("data").path("rowCount").asInt()).isEqualTo(rows);
  }

  @Then("the run was COMPLETED and archived when the event could be read")
  public void completedAndArchived() throws Exception {
    JsonNode run = api.listed(lastRunId());
    assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(run.path("counts").path("archiveGeneration").asInt()).isEqualTo(1);
  }

  @Then("downloading the file of that run answers 404")
  public void noFile() throws Exception {
    assertThat(api.fileStatus(lastRunId())).isEqualTo(404);
  }

  @Then("no archive generation was added")
  public void noGeneration() {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM extract_archive", Integer.class)).isZero();
  }

  @Then("no ExtractProduced event was published")
  public void noEvent() {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE type = 'ExtractProduced'", Integer.class))
        .isZero();
    assertThat(KafkaEvents.extractProduced(lastRunId(), Duration.ofSeconds(6), Duration.ZERO))
        .isEmpty();
  }

  @Then("the run ended long before the answer of provider-service would have come")
  public void endedEarly() {
    assertThat(elapsedMillis).isLessThan(TestProviderService.SLOW_MILLIS - 3000L);
  }

  @Then("the list shows a COMPLETED run with the business date {string}")
  public void listShowsCompleted(String date) throws Exception {
    JsonNode run = api.listed(lastRunId());
    assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(run.path("businessDate").asText()).isEqualTo(date);
  }

  @Then("the start answers {int}, the list answers {int} and the download answers {int}")
  public void statuses(int start, int list, int download) {
    assertThat(callerStart.getResponse().getStatus()).as("start").isEqualTo(start);
    assertThat(callerList.getResponse().getStatus()).as("list").isEqualTo(list);
    assertThat(callerDownload.getResponse().getStatus()).as("download").isEqualTo(download);
  }

  @Then("the extract answers 400 problem+json")
  public void badRequest() {
    assertThat(startResult.getResponse().getStatus()).isEqualTo(400);
    assertThat(startResult.getResponse().getContentType()).startsWith("application/problem+json");
  }

  @Then("no extract run was created")
  public void noRunCreated() {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM extract_run", Integer.class))
        .isEqualTo(rememberedRuns);
  }

  // ---- Then: the warning (MAM-78) ---------------------------------------------------------------

  @Then("the log holds exactly one line {string}")
  public void logHoldsOneLine(String line) {
    assertThat(log.messages(Level.WARN)).filteredOn(m -> m.equals(line)).hasSize(1);
    assertThat(log.messages(Level.WARN))
        .filteredOn(m -> m.startsWith("PRVTMS01: FUTURE ENROLLMENT?"))
        .containsExactly(line);
  }

  @Then("no warning line names the NPIs {string}")
  public void noWarningFor(String npis) {
    for (String npi : npis.split(",")) {
      assertThat(log.lines())
          .extracting(LogCapture.Line::message)
          .noneMatch(m -> m.startsWith("PRVTMS01: FUTURE ENROLLMENT?") && m.contains(npi));
    }
  }

  @Then("the run counts {int} flagged future enrolments")
  public void flagged(int count) {
    assertThat(listedRun.path("counts").path("futureEnrolments").asInt(-1)).isEqualTo(count);
  }
}
