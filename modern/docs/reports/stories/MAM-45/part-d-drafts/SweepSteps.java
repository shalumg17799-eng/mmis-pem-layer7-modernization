package gov.medicaid.pem.revalidation.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.testsupport.EventProbe;
import gov.medicaid.pem.testsupport.PemContainers;
import gov.medicaid.pem.testsupport.PemJwt;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Steps for MAM-45 (the monthly revalidation sweep and its REVALQ file). */
public class SweepSteps {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private MvcResult result;
  private String lastRunId;

  static RequestPostProcessor token(String role) {
    return switch (role) {
      case "ANALYST" -> PemJwt.analyst();
      case "SUPERVISOR" -> PemJwt.supervisor();
      case "ADMIN" -> PemJwt.admin();
      case "SYSTEM" -> PemJwt.system();
      case "PROVIDER" -> PemJwt.provider("1003000111");
      default -> null;
    };
  }

  private MvcResult call(MockHttpServletRequestBuilder req, String role) throws Exception {
    RequestPostProcessor t = token(role);
    return mvc.perform(t == null ? req : req.with(t)).andReturn();
  }

  // ---- given ------------------------------------------------------------------------------------

  @Given("revalidation-service has no tracked providers, cases or sweep runs")
  public void empty() {
    jdbc.update("DELETE FROM revalidation_case");
    jdbc.update("DELETE FROM sweep_run");
    jdbc.update("DELETE FROM tracked_provider");
    jdbc.update("DELETE FROM outbox_event");
  }

  @Given(
      "revalidation-service has consumed ProviderStatusChanged for {string} to status {string} with revalidation due date {string}")
  public void statusChanged(String npi, String status, String due) throws Exception {
    UUID id = UUID.randomUUID();
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("npi", npi);
    data.put("from", "P");
    data.put("to", status);
    data.put("actor", "analyst1");
    data.put("revalidationDueDate", due.equals("none") ? null : due);
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("id", id.toString());
    e.put("type", "ProviderStatusChanged");
    e.put("source", "provider-service");
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
      producer
          .send(new ProducerRecord<>("pem.provider.events", npi, json.writeValueAsString(e)))
          .get();
    }
    long end = System.nanoTime() + Duration.ofSeconds(45).toNanos();
    while (System.nanoTime() < end) {
      Integer n =
          jdbc.queryForObject(
              "SELECT count(*) FROM processed_event WHERE event_id = ?", Integer.class, id);
      if (n != null && n > 0) {
        return;
      }
      Thread.sleep(200);
    }
    throw new AssertionError("ProviderStatusChanged " + id + " was not processed within 45 s");
  }

  // ---- the sweep --------------------------------------------------------------------------------

  private void start(String role, String body) throws Exception {
    result =
        call(
            post("/api/v1/batch/revalidation-sweeps")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
            role);
    if (result.getResponse().getStatus() == 202) {
      lastRunId =
          json.readTree(result.getResponse().getContentAsString()).path("runId").asText();
    }
  }

  @When("{string} starts a revalidation sweep for business date {string}")
  public void startFor(String role, String date) throws Exception {
    start(role, "{\"businessDate\":\"" + date + "\"}");
  }

  @When("{string} starts a revalidation sweep without a business date")
  public void startWithout(String role) throws Exception {
    start(role, "{}");
  }

  @Then("the sweep answer is 202 with a RUNNING revalidation-sweep run for business date {string}")
  public void running(String date) throws Exception {
    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(202);
    JsonNode run = json.readTree(result.getResponse().getContentAsString());
    assertThat(run.path("runId").asText()).matches("[0-9a-f-]{36}");
    assertThat(run.path("job").asText()).containsIgnoringCase("revalidation");
    assertThat(run.path("businessDate").asText()).isEqualTo(date);
    assertThat(run.path("status").asText()).isEqualTo("RUNNING");
  }

  @Then("the sweep answer is {int}")
  public void answer(int status) {
    assertThat(result.getResponse().getStatus()).isEqualTo(status);
  }

  private JsonNode run(String runId, String role) throws Exception {
    MvcResult r = call(get("/api/v1/batch/revalidation-sweeps/{runId}", runId), role);
    assertThat(r.getResponse().getStatus())
        .as(r.getResponse().getContentAsString())
        .isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString());
  }

  @When("the revalidation sweep runs for business date {string} and completes")
  public void runsAndCompletes(String date) throws Exception {
    startFor("SYSTEM", date);
    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(202);
    long end = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (System.nanoTime() < end) {
      if (!run(lastRunId, "ADMIN").path("status").asText().equals("RUNNING")) {
        return;
      }
      Thread.sleep(200);
    }
    throw new AssertionError("sweep run " + lastRunId + " still RUNNING after 30 s");
  }

  // ---- cases ------------------------------------------------------------------------------------

  private JsonNode cases(String npi) throws Exception {
    MvcResult r = call(get("/api/v1/revalidations").param("npi", npi), "ANALYST");
    assertThat(r.getResponse().getStatus())
        .as(r.getResponse().getContentAsString())
        .isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString());
  }

  @Then("the cases of {string} are exactly {int} open case due {string}")
  public void openCase(String npi, int count, String due) throws Exception {
    JsonNode page = cases(npi);
    assertThat(page.path("total").asInt()).isEqualTo(count);
    assertThat(page.path("items")).hasSize(count);
    for (JsonNode c : page.path("items")) {
      assertThat(c.path("npi").asText()).isEqualTo(npi);
      assertThat(c.path("status").asText()).isIn("DUE", "IN_SCREENING");
      assertThat(c.path("dueDate").asText()).isEqualTo(due);
      assertThat(c.path("caseId").asText()).matches("[0-9a-f-]{36}");
    }
  }

  @Then("{string} has no revalidation case")
  public void noCase(String npi) throws Exception {
    JsonNode page = cases(npi);
    assertThat(page.path("total").asInt()).isZero();
    assertThat(page.path("items")).isEmpty();
  }

  // ---- events -----------------------------------------------------------------------------------

  private List<JsonNode> dueEvents(String npi) throws Exception {
    List<JsonNode> out = new ArrayList<>();
    for (String p :
        jdbc.queryForList(
            "SELECT payload::text FROM outbox_event WHERE type = 'RevalidationDue' AND subject = ?",
            String.class,
            npi)) {
      out.add(json.readTree(p));
    }
    return out;
  }

  @Then(
      "exactly {int} RevalidationDue event is written for {string} with the case id and due date {string}")
  public void dueEvent(int count, String npi, String due) throws Exception {
    List<JsonNode> events = dueEvents(npi);
    assertThat(events).hasSize(count);
    JsonNode d = events.get(0).path("data");
    assertThat(d.path("npi").asText()).isEqualTo(npi);
    assertThat(d.path("dueDate").asText()).isEqualTo(due);
    assertThat(d.path("caseId").asText())
        .isEqualTo(cases(npi).path("items").get(0).path("caseId").asText());
  }

  @Then("the RevalidationDue event for {string} reaches topic {string}")
  public void onTopic(String npi, String topic) throws Exception {
    String id = dueEvents(npi).get(0).path("id").asText();
    JsonNode found =
        EventProbe.await(
            PemContainers.KAFKA.getBootstrapServers(),
            topic,
            n -> n.path("id").asText().equals(id),
            Duration.ofSeconds(30));
    assertThat(found.path("type").asText()).isEqualTo("RevalidationDue");
    assertThat(found.path("subject").asText()).isEqualTo(npi);
  }

  // ---- the run and its file ---------------------------------------------------------------------

  private MvcResult queueFile(String runId, String role) throws Exception {
    return call(get("/api/v1/batch/revalidation-sweeps/{runId}/queue-file", runId), role);
  }

  @Then("the queue file of the last run is empty")
  public void emptyFile() throws Exception {
    MvcResult r = queueFile(lastRunId, "ADMIN");
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(r.getResponse().getContentAsByteArray()).isEmpty();
  }

  @Then("the queue file of the last run read by {string} holds exactly these 80-byte records:")
  public void fileRecords(String role, DataTable table) throws Exception {
    MvcResult r = queueFile(lastRunId, role);
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(r.getResponse().getContentType()).startsWith("text/plain");
    byte[] body = r.getResponse().getContentAsByteArray();
    List<Map<String, String>> rows = table.asMaps();
    assertThat(body).hasSize(80 * rows.size());
    for (int i = 0; i < rows.size(); i++) {
      String record = new String(body, i * 80, 80, StandardCharsets.ISO_8859_1);
      Map<String, String> row = rows.get(i);
      String expected =
          row.get("npi")
              + row.get("dueDate")
              + String.format("%-20s", row.get("action"))
              + " ".repeat(42);
      assertThat(record).isEqualTo(expected);
    }
  }

  @Then("the queue file of the last run answers 403 for {string}, {string} and {string}")
  public void fileForbidden(String a, String b, String c) throws Exception {
    for (String role : List.of(a, b, c)) {
      assertThat(queueFile(lastRunId, role).getResponse().getStatus()).as(role).isEqualTo(403);
    }
  }

  @Then("the queue file of an unknown run answers 404")
  public void fileUnknown() throws Exception {
    MvcResult r = queueFile(UUID.randomUUID().toString(), "ADMIN");
    assertThat(r.getResponse().getStatus()).isEqualTo(404);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
  }

  @Then("the last run read by {string} is COMPLETED with {int} queued and {int} without due date")
  public void completed(String role, int queued, int noDueDate) throws Exception {
    JsonNode run = run(lastRunId, role);
    assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(run.path("counts").path("queued").asInt(-1)).isEqualTo(queued);
    assertThat(run.path("counts").path("noDueDate").asInt(-1)).isEqualTo(noDueDate);
    assertThat(run.path("endedAt").asText()).isNotBlank();
  }
}
