package gov.medicaid.pem.revalidation.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.revalidation.ContractFile;
import gov.medicaid.pem.testsupport.PemContainers;
import gov.medicaid.pem.testsupport.PemJwt;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Steps for MAM-45 (the monthly sweep and the REVALQ file), MAM-48 (list and complete cases) and
 * MAM-49 (case stages). Black box: the REST API through MockMvc with signed-in users, the events on
 * a real Kafka, the data in a real Postgres (Testcontainers). The only direct database writes are
 * fixtures (cases in a given status) and the one injected write failure of MAM-45 AC-8.
 */
public class RevalidationSteps {

  private static final String CONTRACT = "revalidation-service";
  private static final String PROVIDER_TOPIC = "pem.provider.events";
  private static final String SCREENING_TOPIC = "pem.screening.events";
  private static final String SWEEPS = "/api/v1/batch/revalidation-sweeps";
  private static final Duration LONG = Duration.ofSeconds(45);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private MvcResult result;
  private final List<UUID> runs = new ArrayList<>();
  private UUID lastEventId;
  private final Map<String, String> snapshots = new HashMap<>();

  // ---- helpers ---------------------------------------------------------------------------------

  private static RequestPostProcessor token(String role) {
    return switch (role) {
      case "ANALYST" -> PemJwt.analyst();
      case "SUPERVISOR" -> PemJwt.supervisor();
      case "ADMIN" -> PemJwt.admin();
      case "SYSTEM" -> PemJwt.system();
      case "PROVIDER" -> PemJwt.provider("1003000111");
      case "NONE" -> null;
      default -> throw new IllegalArgumentException("unknown role " + role);
    };
  }

  private MvcResult call(MockHttpServletRequestBuilder request, String role) throws Exception {
    RequestPostProcessor t = token(role);
    return mvc.perform(t == null ? request : request.with(t)).andReturn();
  }

  private JsonNode body() throws Exception {
    return json.readTree(result.getResponse().getContentAsString());
  }

  private void status(int expected) throws Exception {
    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(expected);
  }

  private void problem(int expected) throws Exception {
    status(expected);
    assertThat(result.getResponse().getContentType()).contains("problem+json");
    JsonNode p = body();
    assertThat(p.path("title").asText()).isNotBlank();
    assertThat(p.path("status").asInt()).isEqualTo(expected);
  }

  /**
   * The last answer conforms to the schema the contract documents for this operation and status.
   */
  private void contract(String operation, int status) throws Exception {
    assertThat(ContractFile.responseViolations(operation, status, body()))
        .as("%s %s: %s", operation, status, result.getResponse().getContentAsString())
        .isEmpty();
  }

  private UUID lastRun() {
    assertThat(runs).as("a sweep run was started in this scenario").isNotEmpty();
    return runs.get(runs.size() - 1);
  }

  private UUID caseIdOf(String npi) {
    return jdbc.queryForObject(
        "SELECT public_id FROM revalidation_case WHERE npi = ? ORDER BY id DESC LIMIT 1",
        UUID.class,
        npi);
  }

  // ---- events ----------------------------------------------------------------------------------

  private UUID send(String topic, String source, String type, String npi, Map<String, Object> data)
      throws Exception {
    UUID id = UUID.randomUUID();
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("id", id.toString());
    e.put("type", type);
    e.put("source", source);
    e.put("time", "2026-11-19T10:00:00Z");
    e.put("subject", npi);
    e.put("correlationId", UUID.randomUUID().toString());
    e.put("data", data);
    Map<String, Object> cfg =
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, PemContainers.KAFKA.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    try (var producer = new KafkaProducer<String, String>(cfg)) {
      producer.send(new ProducerRecord<>(topic, npi, json.writeValueAsString(e))).get();
    }
    awaitProcessed(id);
    lastEventId = id;
    return id;
  }

  private void awaitProcessed(UUID id) {
    Awaitility.await("event " + id + " processed by revalidation-service")
        .atMost(LONG)
        .pollInterval(Duration.ofMillis(200))
        .until(
            () ->
                jdbc.queryForObject(
                        "SELECT count(*) FROM processed_event WHERE event_id = ?",
                        Integer.class,
                        id)
                    > 0);
  }

  private void providerStatusChanged(String npi, String from, String to, String due)
      throws Exception {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("npi", npi);
    data.put("from", from);
    data.put("to", to);
    data.put("revalidationDueDate", due);
    send(PROVIDER_TOPIC, "provider-service", "ProviderStatusChanged", npi, data);
  }

  @Given("the revalidation tables are empty")
  public void empty() {
    dropFailure();
    jdbc.update("DELETE FROM revalidation_case");
    jdbc.update("DELETE FROM sweep_run");
    jdbc.update("DELETE FROM tracked_provider");
    jdbc.update("DELETE FROM outbox_event");
  }

  @After
  public void finish() {
    dropFailure();
    for (UUID run : runs) {
      Awaitility.await("sweep run " + run + " finished")
          .atMost(LONG)
          .pollInterval(Duration.ofMillis(200))
          .until(() -> !"RUNNING".equals(runStatus(run)));
    }
  }

  private String runStatus(UUID run) {
    var rows = jdbc.queryForList("SELECT status FROM sweep_run WHERE public_id = ?", run);
    return rows.isEmpty() ? "GONE" : rows.get(0).get("status").toString().trim();
  }

  @Given("the revalidation outbox is empty")
  public void emptyOutbox() {
    jdbc.update("DELETE FROM outbox_event");
  }

  @Given(
      "revalidation-service tracks provider {string} as status {string} with revalidation due date {string}")
  public void tracks(String npi, String status, String due) throws Exception {
    providerStatusChanged(npi, "P", status, due);
  }

  @Given(
      "revalidation-service tracks provider {string} as status {string} with no revalidation due date")
  public void tracksWithoutDate(String npi, String status) throws Exception {
    providerStatusChanged(npi, "P", status, null);
  }

  @When(
      "provider-service publishes ProviderStatusChanged for {string} from {string} to {string} with revalidation due date {string}")
  public void statusChanged(String npi, String from, String to, String due) throws Exception {
    providerStatusChanged(npi, from, to, due);
  }

  @When(
      "screening-service publishes ScreeningCompleted with reason {string} and result {string} for {string}")
  public void screeningCompleted(String reason, String result, String npi) throws Exception {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("screeningId", UUID.randomUUID().toString());
    data.put("npi", npi);
    data.put("reason", reason);
    data.put("result", result);
    send(SCREENING_TOPIC, "screening-service", "ScreeningCompleted", npi, data);
  }

  @Then("the event was processed without error")
  public void processedWithoutError() {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM processed_event WHERE event_id = ?",
                Integer.class,
                lastEventId))
        .isEqualTo(1);
  }

  @Then("the tracked due date of NPI {string} is still {string}")
  public void trackedDue(String npi, String due) {
    assertThat(
            jdbc.queryForObject(
                "SELECT reval_due_date::text FROM tracked_provider WHERE npi = ?",
                String.class,
                npi))
        .isEqualTo(due);
  }

  // ---- cases (fixtures) ------------------------------------------------------------------------

  @Given("a {string} case for NPI {string} with due date {string}")
  public void caseExists(String status, String npi, String due) {
    boolean closed = status.equals("COMPLETED") || status.equals("CANCELLED");
    jdbc.update(
        "INSERT INTO revalidation_case (public_id, npi, due_date, status, opened_at, closed_at)"
            + " VALUES (?, ?, ?, ?, ?, ?)",
        UUID.randomUUID(),
        npi,
        Date.valueOf(due),
        status,
        Timestamp.from(Instant.parse("2026-03-01T10:00:00Z")),
        closed ? Timestamp.from(Instant.parse("2026-03-15T10:00:00Z")) : null);
    snapshots.put(npi, snapshot(npi));
  }

  private String snapshot(String npi) {
    return String.join(
        ";",
        jdbc.queryForList(
            "SELECT public_id || '|' || status || '|' || due_date || '|' || coalesce(closed_at::text, '')"
                + " || '|' || version FROM revalidation_case WHERE npi = ? ORDER BY id",
            String.class,
            npi));
  }

  @Then("the case of NPI {string} is unchanged")
  public void unchanged(String npi) {
    assertThat(snapshot(npi)).isEqualTo(snapshots.get(npi));
  }

  @Then("the case of NPI {string} is still {string}")
  public void stillIn(String npi, String status) {
    assertThat(caseStatus(npi)).isEqualTo(status);
    assertThat(snapshot(npi).split(";")).hasSize(1);
  }

  @Then("the case of NPI {string} is {string}")
  public void isIn(String npi, String status) {
    assertThat(caseStatus(npi)).isEqualTo(status);
  }

  private String caseStatus(String npi) {
    return jdbc.queryForObject(
        "SELECT status FROM revalidation_case WHERE npi = ? ORDER BY id DESC LIMIT 1",
        String.class,
        npi);
  }

  // ---- the sweep -------------------------------------------------------------------------------

  private void startSweep(String role, String requestBody) throws Exception {
    result = call(post(SWEEPS).contentType(MediaType.APPLICATION_JSON).content(requestBody), role);
    if (result.getResponse().getStatus() == 202) {
      runs.add(UUID.fromString(body().path("runId").asText()));
    }
  }

  @When("{string} starts the revalidation sweep for business date {string}")
  public void starts(String role, String date) throws Exception {
    startSweep(role, "{\"businessDate\":\"" + date + "\"}");
  }

  @When("{string} starts the revalidation sweep without a business date")
  public void startsWithout(String role) throws Exception {
    startSweep(role, "{}");
  }

  private JsonNode awaitRun(UUID run) throws Exception {
    Awaitility.await("sweep run " + run + " finished")
        .atMost(LONG)
        .pollInterval(Duration.ofMillis(200))
        .until(() -> !"RUNNING".equals(runStatus(run)));
    MvcResult r = call(get(SWEEPS + "/{runId}", run), "ADMIN");
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString());
  }

  @When("the revalidation sweep runs for {string}")
  public void runs(String date) throws Exception {
    startSweep("ADMIN", "{\"businessDate\":\"" + date + "\"}");
    status(202);
    JsonNode run = awaitRun(lastRun());
    assertThat(run.path("status").asText()).as(run.toString()).isEqualTo("COMPLETED");
  }

  @When("the revalidation sweep is started for {string} and finishes")
  public void startedAndFinishes(String date) throws Exception {
    startSweep("ADMIN", "{\"businessDate\":\"" + date + "\"}");
    status(202);
    awaitRun(lastRun());
  }

  @Then(
      "the sweep start answer is {int} with a BatchRun for business date {string}, job {string} and status {string}")
  public void batchRun(int status, String date, String job, String runStatus) throws Exception {
    status(status);
    contract("startRevalidationSweep", 202);
    JsonNode run = body();
    assertThat(UUID.fromString(run.path("runId").asText())).isEqualTo(lastRun());
    assertThat(run.path("job").asText()).isEqualTo(job);
    assertThat(run.path("businessDate").asText()).isEqualTo(date);
    assertThat(run.path("status").asText()).isEqualTo(runStatus);
    assertThat(run.path("startedAt").asText()).isNotBlank();
  }

  @Then("the sweep start answer is {int} with a problem")
  public void startProblem(int status) throws Exception {
    problem(status);
    contract("startRevalidationSweep", status);
  }

  @Then("the sweep start answer is {int}")
  public void startAnswer(int status) throws Exception {
    status(status);
  }

  @Then("no sweep run is recorded")
  public void noRun() {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM sweep_run", Integer.class)).isZero();
  }

  // ---- run status (MAM-45 AC-8, contract 1.1.0) ------------------------------------------------

  private void readRun(String role, UUID run) throws Exception {
    result = call(get(SWEEPS + "/{runId}", run), role);
  }

  @When("{string} reads the last run")
  public void readsLast(String role) throws Exception {
    readRun(role, lastRun());
  }

  @When("{string} reads the run {string}")
  public void readsRun(String role, String run) throws Exception {
    readRun(role, UUID.fromString(run));
  }

  @Then("the run answer is {int}")
  public void runAnswer(int status) throws Exception {
    status(status);
  }

  @Then(
      "{string} reads the last run as a BatchRun with status {string}, queued {int}, noDueDate {int}, business date {string}, a startedAt, an endedAt and no error")
  public void runCounts(String role, String status, int queued, int noDueDate, String date)
      throws Exception {
    readRun(role, lastRun());
    status(200);
    contract("getRevalidationSweep", 200);
    JsonNode run = body();
    assertThat(UUID.fromString(run.path("runId").asText())).isEqualTo(lastRun());
    assertThat(run.path("job").asText()).isEqualTo("revalidation-sweep");
    assertThat(run.path("status").asText()).isEqualTo(status);
    assertThat(run.path("businessDate").asText()).isEqualTo(date);
    assertThat(run.path("counts").path("queued").asInt()).isEqualTo(queued);
    assertThat(run.path("counts").path("noDueDate").asInt()).isEqualTo(noDueDate);
    assertThat(run.path("startedAt").asText()).isNotBlank();
    assertThat(run.path("endedAt").asText()).isNotBlank();
    assertThat(run.path("error").isMissingNode() || run.path("error").isNull()).isTrue();
  }

  @Then(
      "{string} reads the last run as a BatchRun with status {string} and an error containing {string}")
  public void failedRun(String role, String status, String reason) throws Exception {
    readRun(role, lastRun());
    status(200);
    contract("getRevalidationSweep", 200);
    JsonNode run = body();
    assertThat(run.path("status").asText()).isEqualTo(status);
    assertThat(run.path("error").asText()).contains(reason);
    assertThat(run.path("endedAt").asText()).isNotBlank();
  }

  private void countsOf(UUID run, int queued, int noDueDate) throws Exception {
    readRun("ADMIN", run);
    status(200);
    JsonNode counts = body().path("counts");
    assertThat(counts.path("queued").asInt()).isEqualTo(queued);
    assertThat(counts.path("noDueDate").asInt()).isEqualTo(noDueDate);
  }

  @Then("the run counts show queued {int} and noDueDate {int}")
  public void countsLast(int queued, int noDueDate) throws Exception {
    countsOf(lastRun(), queued, noDueDate);
  }

  @Then("the run counts of run {int} show queued {int} and noDueDate {int}")
  public void countsOfRun(int n, int queued, int noDueDate) throws Exception {
    countsOf(runs.get(n - 1), queued, noDueDate);
  }

  // ---- the injected write failure (MAM-45 AC-8) ------------------------------------------------

  @Given("writing a revalidation case fails with {string}")
  public void writeFails(String reason) {
    dropFailure();
    jdbc.execute(
        "CREATE FUNCTION revalidation_test_fail() RETURNS trigger AS $$ BEGIN RAISE EXCEPTION '"
            + reason
            + "'; END; $$ LANGUAGE plpgsql");
    jdbc.execute(
        "CREATE TRIGGER revalidation_test_fail BEFORE INSERT ON revalidation_case"
            + " FOR EACH ROW EXECUTE FUNCTION revalidation_test_fail()");
  }

  @Given("writing a revalidation case works again")
  public void writeWorks() {
    dropFailure();
  }

  private void dropFailure() {
    jdbc.execute("DROP TRIGGER IF EXISTS revalidation_test_fail ON revalidation_case");
    jdbc.execute("DROP FUNCTION IF EXISTS revalidation_test_fail()");
  }

  // ---- the REVALQ file -------------------------------------------------------------------------

  private void readFile(String role, UUID run) throws Exception {
    result = call(get(SWEEPS + "/{runId}/queue-file", run), role);
  }

  @When("{string} reads the REVALQ file of the last run")
  public void readsFile(String role) throws Exception {
    readFile(role, lastRun());
  }

  @When("{string} reads the REVALQ file of run {string}")
  public void readsFileOf(String role, String run) throws Exception {
    readFile(role, UUID.fromString(run));
  }

  @Then("the file answer is {int}")
  public void fileAnswer(int status) throws Exception {
    status(status);
  }

  private List<String> records(UUID run) throws Exception {
    readFile("ADMIN", run);
    status(200);
    assertThat(result.getResponse().getContentType()).startsWith("text/plain");
    String file =
        new String(result.getResponse().getContentAsByteArray(), StandardCharsets.ISO_8859_1);
    assertThat(file.length() % 80).as("the file is made of 80-byte records").isZero();
    List<String> rows = new ArrayList<>();
    for (int i = 0; i < file.length(); i += 80) {
      rows.add(file.substring(i, i + 80));
    }
    return rows;
  }

  @Then("the REVALQ file of the last run has {int} record(s)")
  public void recordsOfLast(int n) throws Exception {
    assertThat(records(lastRun())).hasSize(n);
  }

  @Then("the REVALQ file of run {int} has {int} record(s)")
  public void recordsOfRun(int run, int n) throws Exception {
    assertThat(records(runs.get(run - 1))).hasSize(n);
  }

  @Then("the REVALQ file of the last run is not found")
  public void fileNotFound() throws Exception {
    readFile("ADMIN", lastRun());
    problem(404);
  }

  @Then("the REVALQ file of the last run is a text file with these {int} records of 80 bytes")
  public void recordLayout(int n, DataTable table) throws Exception {
    List<String> rows = records(lastRun());
    List<Map<String, String>> expected = table.asMaps();
    assertThat(rows).hasSize(n);
    assertThat(expected).hasSize(n);
    for (int i = 0; i < n; i++) {
      String r = rows.get(i);
      assertThat(r).hasSize(80);
      assertThat(r.substring(0, 10))
          .as("REV-NPI of record " + (i + 1))
          .isEqualTo(expected.get(i).get("npi"));
      assertThat(r.substring(10, 18))
          .as("REV-DUE-DATE (CCYYMMDD) of record " + (i + 1))
          .isEqualTo(expected.get(i).get("dueDate"));
      assertThat(r.substring(18, 38))
          .as("REV-ACTION of record " + (i + 1))
          .isEqualTo("RESCREEN-REQUIRED   ");
      assertThat(r.substring(38)).as("filler of record " + (i + 1)).isEqualTo(" ".repeat(42));
    }
  }

  // ---- events written by the service -----------------------------------------------------------

  private List<JsonNode> outbox(String npi, String type) throws Exception {
    List<JsonNode> events = new ArrayList<>();
    for (String payload :
        jdbc.queryForList(
            "SELECT payload::text FROM outbox_event WHERE subject = ? AND type = ? ORDER BY created_at",
            String.class,
            npi,
            type)) {
      events.add(json.readTree(payload));
    }
    return events;
  }

  @Then(
      "exactly {int} RevalidationDue event(s) is/are written for NPI {string} with the case id, npi {string} and due date {string}")
  public void dueEvents(int count, String npi, String dataNpi, String due) throws Exception {
    List<JsonNode> events = outbox(npi, "RevalidationDue");
    assertThat(events).hasSize(count);
    JsonNode e = events.get(0);
    assertThat(UUID.fromString(e.path("id").asText())).isNotNull();
    assertThat(e.path("type").asText()).isEqualTo("RevalidationDue");
    assertThat(e.path("source").asText()).isEqualTo("revalidation-service");
    assertThat(e.path("subject").asText()).isEqualTo(npi);
    assertThat(e.path("correlationId").asText()).isNotBlank();
    assertThat(e.path("time").asText()).isNotBlank();
    JsonNode data = e.path("data");
    assertThat(data.path("caseId").asText()).isEqualTo(caseIdOf(npi).toString());
    assertThat(data.path("npi").asText()).isEqualTo(dataNpi);
    assertThat(data.path("dueDate").asText()).isEqualTo(due);
    lastEventId = UUID.fromString(e.path("id").asText());
  }

  @Then("that RevalidationDue event reaches topic {string} with message key {string}")
  public void onTopic(String topic, String key) {
    Map<String, Object> cfg =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            PemContainers.KAFKA.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG,
            "probe-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class);
    ConsumerRecord<String, String> found = null;
    try (var consumer = new KafkaConsumer<String, String>(cfg)) {
      consumer.subscribe(List.of(topic));
      long end = System.nanoTime() + LONG.toNanos();
      while (found == null && System.nanoTime() < end) {
        for (var rec : consumer.poll(Duration.ofMillis(250))) {
          if (rec.value().contains(lastEventId.toString())) {
            found = rec;
          }
        }
      }
    }
    assertThat(found).as("event " + lastEventId + " on " + topic).isNotNull();
    assertThat(found.key()).isEqualTo(key);
  }

  @Then("no RevalidationDue event is written for NPI {string}")
  public void noDueEvent(String npi) throws Exception {
    assertThat(outbox(npi, "RevalidationDue")).isEmpty();
  }

  @Then("revalidation-service wrote no event for NPI {string}")
  public void noEvent(String npi) {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE subject = ?", Integer.class, npi))
        .isZero();
  }

  @When("the RevalidationDue event of NPI {string} has been published")
  public void dueEventPublished(String npi) {
    Awaitility.await("RevalidationDue of " + npi + " published")
        .atMost(LONG)
        .pollInterval(Duration.ofMillis(200))
        .until(
            () ->
                jdbc.queryForObject(
                        "SELECT count(*) FROM outbox_event WHERE subject = ? AND type = 'RevalidationDue'"
                            + " AND published_at IS NOT NULL",
                        Integer.class,
                        npi)
                    > 0);
  }

  // ---- the case list (MAM-48 AC-1, MAM-45 AC-2) ------------------------------------------------

  private MvcResult list(String role, Map<String, String> query) throws Exception {
    MockHttpServletRequestBuilder req = get("/api/v1/revalidations");
    query.forEach(req::param);
    return call(req, role);
  }

  private JsonNode casesOf(String npi) throws Exception {
    MvcResult r = list("ANALYST", Map.of("npi", npi, "size", "200"));
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString()).path("items");
  }

  private List<JsonNode> casesOf(String npi, String status) throws Exception {
    List<JsonNode> out = new ArrayList<>();
    casesOf(npi)
        .forEach(
            c -> {
              if (status == null || c.path("status").asText().equals(status)) {
                out.add(c);
              }
            });
    return out;
  }

  @When("{string} lists revalidation cases with status {string}, page {int} and size {int}")
  public void listsByStatus(String role, String status, int page, int size) throws Exception {
    result =
        list(
            role,
            Map.of("status", status, "page", String.valueOf(page), "size", String.valueOf(size)));
  }

  @When("{string} lists revalidation cases for NPI {string}")
  public void listsByNpi(String role, String npi) throws Exception {
    result = list(role, Map.of("npi", npi));
  }

  @When("{string} lists revalidation cases with the query {string}")
  public void listsByQuery(String role, String query) throws Exception {
    MockHttpServletRequestBuilder req = get("/api/v1/revalidations");
    for (String pair : query.split("&")) {
      String[] kv = pair.split("=", 2);
      req.param(kv[0], kv[1]);
    }
    result = call(req, role);
  }

  @Then("the list answer is {int} with a problem")
  public void listProblem(int status) throws Exception {
    problem(status);
    contract("listRevalidationCases", status);
  }

  @Then("the list answer is {int}")
  public void listAnswer(int status) throws Exception {
    status(status);
  }

  @Then(
      "the list answer is {int} with total {int}, page {int} and size {int} and exactly the cases of NPI {string}")
  public void listOf(int status, int total, int page, int size, String npis) throws Exception {
    status(status);
    contract("listRevalidationCases", 200);
    JsonNode p = body();
    assertThat(p.path("total").asInt()).isEqualTo(total);
    assertThat(p.path("page").asInt()).isEqualTo(page);
    assertThat(p.path("size").asInt()).isEqualTo(size);
    List<String> listed = new ArrayList<>();
    p.path("items").forEach(c -> listed.add(c.path("npi").asText()));
    assertThat(listed).containsExactlyElementsOf(List.of(npis.split(",")));
  }

  @Then(
      "every listed case has a caseId, an npi, a dueDate, a status {string} and an openedAt, and no closedAt")
  public void listedShape(String status) throws Exception {
    JsonNode items = body().path("items");
    assertThat(items).isNotEmpty();
    for (JsonNode c : items) {
      assertThat(UUID.fromString(c.path("caseId").asText())).isNotNull();
      assertThat(c.path("npi").asText()).matches("[0-9]{10}");
      assertThat(c.path("dueDate").asText()).matches("[0-9]{4}-[0-9]{2}-[0-9]{2}");
      assertThat(c.path("status").asText()).isEqualTo(status);
      assertThat(c.path("openedAt").asText()).isNotBlank();
      assertThat(c.path("closedAt").isMissingNode() || c.path("closedAt").isNull()).isTrue();
    }
  }

  @Then("every listed case has the status {string}")
  public void listedStatus(String status) throws Exception {
    JsonNode items = body().path("items");
    assertThat(items).isNotEmpty();
    for (JsonNode c : items) {
      assertThat(c.path("status").asText()).isEqualTo(status);
    }
  }

  @Then("every listed case has a closedAt")
  public void listedClosed() throws Exception {
    JsonNode items = body().path("items");
    assertThat(items).isNotEmpty();
    for (JsonNode c : items) {
      assertThat(c.path("closedAt").asText()).isNotBlank();
    }
  }

  @Then("{string} listing revalidation cases with status {string} shows NPI {string}")
  public void listingShows(String role, String status, String npi) throws Exception {
    assertThat(listedNpis(role, status)).contains(npi);
  }

  @Then("{string} listing revalidation cases with status {string} does not show NPI {string}")
  public void listingDoesNotShow(String role, String status, String npi) throws Exception {
    assertThat(listedNpis(role, status)).doesNotContain(npi);
  }

  private List<String> listedNpis(String role, String status) throws Exception {
    MvcResult r = list(role, Map.of("status", status, "size", "200"));
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    List<String> npis = new ArrayList<>();
    json.readTree(r.getResponse().getContentAsString())
        .path("items")
        .forEach(c -> npis.add(c.path("npi").asText()));
    return npis;
  }

  // ---- the cases of one provider ---------------------------------------------------------------

  @Then("NPI {string} has no revalidation case")
  public void noCase(String npi) throws Exception {
    assertThat(casesOf(npi)).isEmpty();
  }

  @Then("NPI {string} has no open case")
  public void noOpenCase(String npi) throws Exception {
    assertThat(openCases(npi)).isEmpty();
  }

  @Then("NPI {string} has exactly {int} case(s)")
  public void caseCount(String npi, int n) throws Exception {
    assertThat(casesOf(npi)).hasSize(n);
  }

  private List<JsonNode> openCases(String npi) throws Exception {
    List<JsonNode> open = new ArrayList<>(casesOf(npi, "DUE"));
    open.addAll(casesOf(npi, "IN_SCREENING"));
    return open;
  }

  @Then("NPI {string} has exactly one open case with due date {string}")
  public void oneOpenCase(String npi, String due) throws Exception {
    List<JsonNode> open = openCases(npi);
    assertThat(open).hasSize(1);
    assertThat(open.get(0).path("dueDate").asText()).isEqualTo(due);
    assertThat(open.get(0).path("npi").asText()).isEqualTo(npi);
  }

  @Then("NPI {string} has a case in status {string} with a closedAt")
  public void caseWithClosedAt(String npi, String status) throws Exception {
    List<JsonNode> cases = casesOf(npi, status);
    assertThat(cases).as("a " + status + " case of " + npi).isNotEmpty();
    assertThat(cases.get(0).path("closedAt").asText()).isNotBlank();
  }

  @Then("NPI {string} has a case in status {string} within {int} seconds")
  public void caseWithin(String npi, String status, int seconds) {
    Awaitility.await("case of " + npi + " in status " + status)
        .atMost(Duration.ofSeconds(seconds))
        .pollInterval(Duration.ofMillis(250))
        .until(() -> !casesOf(npi, status).isEmpty());
  }

  // ---- completing a case (MAM-48) --------------------------------------------------------------

  private void complete(String role, UUID caseId, String requestBody) throws Exception {
    result =
        call(
            post("/api/v1/revalidations/{caseId}/completion", caseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody),
            role);
  }

  private static String completion(String screeningResult) {
    return "{\"screeningId\":\"" + UUID.randomUUID() + "\",\"result\":\"" + screeningResult + "\"}";
  }

  @When("{string} completes the case of NPI {string} with a screening result {string}")
  public void completes(String role, String npi, String screeningResult) throws Exception {
    complete(role, caseIdOf(npi), completion(screeningResult));
  }

  @When("{string} completes the case {string} with a screening result {string}")
  public void completesUnknown(String role, String caseId, String screeningResult)
      throws Exception {
    complete(role, UUID.fromString(caseId), completion(screeningResult));
  }

  @When("{string} completes the case of NPI {string} with the body {string}")
  public void completesWithBody(String role, String npi, String requestBody) throws Exception {
    complete(role, caseIdOf(npi), requestBody);
  }

  @Then("the completion answer is {int}")
  public void completionAnswer(int status) throws Exception {
    status(status);
  }

  @Then("the completion answer is {int} with a problem")
  public void completionProblem(int status) throws Exception {
    problem(status);
    if (status == 404 || status == 409) {
      contract("completeRevalidation", status);
    }
  }

  @Then(
      "the completion answer is {int} with the case of NPI {string} in status {string}, its due date {string} and a closedAt")
  public void completed(int status, String npi, String caseStatus, String due) throws Exception {
    status(status);
    contract("completeRevalidation", 200);
    JsonNode c = body();
    assertThat(c.path("caseId").asText()).isEqualTo(caseIdOf(npi).toString());
    assertThat(c.path("npi").asText()).isEqualTo(npi);
    assertThat(c.path("status").asText()).isEqualTo(caseStatus);
    assertThat(c.path("dueDate").asText()).isEqualTo(due);
    assertThat(c.path("openedAt").asText()).isNotBlank();
    assertThat(c.path("closedAt").asText()).isNotBlank();
  }

  // ---- additions of the final test pass (2026-11-20) -------------------------------------------

  @When("{string} starts the revalidation sweep with the body {string}")
  public void startsWithBody(String role, String requestBody) throws Exception {
    startSweep(role, requestBody);
  }

  @Then("{string} reads the last run as a FAILED BatchRun with a short reason without SQL text")
  public void shortReasonWithoutSql(String role) throws Exception {
    readRun(role, lastRun());
    status(200);
    JsonNode run = body();
    assertThat(run.path("status").asText()).isEqualTo("FAILED");
    String error = run.path("error").asText();
    assertThat(error).isNotBlank().hasSizeLessThanOrEqualTo(200);
    assertThat(error.toLowerCase()).doesNotContain("select", "insert", "update", "sql");
  }
}
