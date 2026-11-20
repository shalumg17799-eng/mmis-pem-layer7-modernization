package gov.medicaid.pem.revalidation.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.testsupport.PemContainers;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
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

/**
 * Steps for MAM-48 (list and complete revalidation cases). Cases are written straight into
 * revalidation_db so that they keep the status the scenario gives them.
 */
public class CaseSteps {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private final Map<String, UUID> caseOf = new HashMap<>();
  private MvcResult result;
  private String firstClosedAt;

  private MvcResult call(MockHttpServletRequestBuilder req, String role) throws Exception {
    RequestPostProcessor t = SweepSteps.token(role);
    return mvc.perform(t == null ? req : req.with(t)).andReturn();
  }

  private void insertCase(String npi, LocalDate due, String status) {
    jdbc.update(
        "INSERT INTO tracked_provider (npi, enroll_status, reval_due_date) VALUES (?, 'A', ?)"
            + " ON CONFLICT (npi) DO UPDATE SET enroll_status = 'A', reval_due_date = EXCLUDED.reval_due_date",
        npi,
        Date.valueOf(due));
    UUID id = UUID.randomUUID();
    boolean closed = status.equals("COMPLETED") || status.equals("CANCELLED");
    Timestamp now = Timestamp.from(Instant.now());
    jdbc.update(
        "INSERT INTO revalidation_case (public_id, npi, due_date, status, opened_at, closed_at)"
            + " VALUES (?, ?, ?, ?, ?, ?)",
        id,
        npi,
        Date.valueOf(due),
        status,
        now,
        closed ? now : null);
    caseOf.put(npi, id);
  }

  @Given("revalidation_db holds these cases:")
  public void holds(DataTable table) {
    for (Map<String, String> row : table.asMaps()) {
      insertCase(row.get("npi"), LocalDate.parse(row.get("dueDate")), row.get("status"));
    }
  }

  @Given("an open revalidation case for {string} due {string}")
  public void openCase(String npi, String due) {
    insertCase(npi, LocalDate.parse(due), "DUE");
  }

  // ---- listing ----------------------------------------------------------------------------------

  @When("{string} lists the revalidation cases with {string}")
  public void list(String role, String query) throws Exception {
    result = call(get("/api/v1/revalidations?" + query), role);
  }

  @Then("the case list answers 200 with the cases of {string}, page {int} and size {int}")
  public void listed(String npi, int page, int size) throws Exception {
    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(200);
    JsonNode p = json.readTree(result.getResponse().getContentAsString());
    assertThat(p.path("page").asInt()).isEqualTo(page);
    assertThat(p.path("size").asInt()).isEqualTo(size);
    assertThat(p.path("total").asInt()).isEqualTo(1);
    JsonNode c = p.path("items").get(0);
    assertThat(c.path("caseId").asText()).isEqualTo(caseOf.get(npi).toString());
    assertThat(c.path("npi").asText()).isEqualTo(npi);
    assertThat(c.path("dueDate").asText()).matches("\\d{4}-\\d{2}-\\d{2}");
    assertThat(c.path("status").asText()).isNotBlank();
    assertThat(c.path("openedAt").asText()).isNotBlank();
  }

  @Then("the case list answers {int} problem+json")
  public void listProblem(int status) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(status);
    assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
  }

  private JsonNode casesOf(String npi, String status) throws Exception {
    MvcResult r =
        call(
            get("/api/v1/revalidations")
                .param("npi", npi)
                .param("size", "200")
                .param(status == null ? "page" : "status", status == null ? "0" : status),
            "ANALYST");
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString());
  }

  private JsonNode caseNode(String npi) throws Exception {
    for (JsonNode c : casesOf(npi, null).path("items")) {
      if (c.path("caseId").asText().equals(caseOf.get(npi).toString())) {
        return c;
      }
    }
    throw new AssertionError("case of " + npi + " is not listed");
  }

  @Then("{string} has no case with status {string}")
  public void noCaseWith(String npi, String status) throws Exception {
    assertThat(casesOf(npi, status).path("total").asInt()).isZero();
  }

  @Then("the case of {string} still has status {string}")
  public void stillHas(String npi, String status) throws Exception {
    assertThat(caseNode(npi).path("status").asText()).isEqualTo(status);
  }

  @Then("the case of {string} is COMPLETED with closedAt set")
  public void completedCase(String npi) throws Exception {
    JsonNode c = caseNode(npi);
    assertThat(c.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(c.path("closedAt").asText()).isNotBlank();
  }

  @Then("the case of {string} is still COMPLETED with its first closedAt")
  public void stillCompleted(String npi) throws Exception {
    JsonNode c = caseNode(npi);
    assertThat(c.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(c.path("closedAt").asText()).isEqualTo(firstClosedAt);
  }

  // ---- completion -------------------------------------------------------------------------------

  private void complete(String role, String caseId, String outcome) throws Exception {
    result =
        call(
            post("/api/v1/revalidations/{caseId}/completion", caseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"screeningId\":\""
                        + UUID.randomUUID()
                        + "\",\"result\":\""
                        + outcome
                        + "\"}"),
            role);
    if (result.getResponse().getStatus() == 200 && firstClosedAt == null) {
      firstClosedAt =
          json.readTree(result.getResponse().getContentAsString()).path("closedAt").asText();
    }
  }

  @When(
      "{string} completes the case of {string} with a new screening id and result {string}")
  public void completes(String role, String npi, String outcome) throws Exception {
    complete(role, caseOf.get(npi).toString(), outcome);
  }

  @When("{string} completes an unknown case with result {string}")
  public void completesUnknown(String role, String outcome) throws Exception {
    complete(role, UUID.randomUUID().toString(), outcome);
  }

  @Then("the completion answers 200 with the case COMPLETED and closedAt set")
  public void completedAnswer() throws Exception {
    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(200);
    JsonNode c = json.readTree(result.getResponse().getContentAsString());
    assertThat(c.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(c.path("closedAt").asText()).isNotBlank();
  }

  @Then("the completion answers {int} problem+json")
  public void completionProblem(int status) throws Exception {
    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(status);
    assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
  }

  // ---- events -----------------------------------------------------------------------------------

  @When(
      "revalidation-service consumes ScreeningCompleted for {string} with reason {string} and result {string}")
  public void screeningCompleted(String npi, String reason, String outcome) throws Exception {
    UUID id = UUID.randomUUID();
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("screeningId", UUID.randomUUID().toString());
    data.put("npi", npi);
    data.put("reason", reason);
    data.put("result", outcome);
    data.put("taxonomy", "207Q00000X");
    data.put("sources", List.of());
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("id", id.toString());
    e.put("type", "ScreeningCompleted");
    e.put("source", "screening-service");
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
          .send(new ProducerRecord<>("pem.screening.events", npi, json.writeValueAsString(e)))
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
    throw new AssertionError("ScreeningCompleted " + id + " was not processed within 45 s");
  }

  @Then("the tracked due date of {string} is still {string}")
  public void trackedDue(String npi, String due) {
    assertThat(
            jdbc.queryForObject(
                    "SELECT reval_due_date FROM tracked_provider WHERE npi = ?", Date.class, npi)
                .toLocalDate()
                .toString())
        .isEqualTo(due);
  }

  @Then("revalidation-service has written no event for {string}")
  public void noEvent(String npi) {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE subject = ?", Integer.class, npi))
        .isZero();
  }

  // ---- roles ------------------------------------------------------------------------------------

  private int listAs(String role) throws Exception {
    return call(get("/api/v1/revalidations"), role).getResponse().getStatus();
  }

  @Then("listing the revalidation cases answers {int} for {string} and {string}")
  public void listRoles2(int status, String a, String b) throws Exception {
    for (String role : List.of(a, b)) {
      assertThat(listAs(role)).as(role).isEqualTo(status);
    }
  }

  @Then("listing the revalidation cases answers {int} for {string}, {string} and {string}")
  public void listRoles3(int status, String a, String b, String c) throws Exception {
    for (String role : List.of(a, b, c)) {
      assertThat(listAs(role)).as(role).isEqualTo(status);
    }
  }

  @Then("listing the revalidation cases answers 401 without a token")
  public void listAnonymous() throws Exception {
    assertThat(listAs("NONE")).isEqualTo(401);
  }

  private int completeAs(String role, String npi) throws Exception {
    complete(role, caseOf.get(npi).toString(), "PASS");
    return result.getResponse().getStatus();
  }

  @Then("completing the case of {string} answers {int} for {string}, {string} and {string}")
  public void completeRoles(String npi, int status, String a, String b, String c)
      throws Exception {
    for (String role : List.of(a, b, c)) {
      assertThat(completeAs(role, npi)).as(role).isEqualTo(status);
    }
  }

  @Then("completing the case of {string} answers 401 without a token")
  public void completeAnonymous(String npi) throws Exception {
    assertThat(completeAs("NONE", npi)).isEqualTo(401);
  }
}
