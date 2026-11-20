package gov.medicaid.pem.provider.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.provider.ProviderRows;
import gov.medicaid.pem.provider.TestEvents;
import gov.medicaid.pem.testsupport.PemJwt;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Steps of MAM-40: the history of a provider is built by the real flows (events from the other
 * services on Kafka, the analyst's approval and the SYSTEM status change through the API) and read
 * back through GET /api/v1/providers/{npi}/audit.
 */
public class ProviderAuditSteps {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  @Autowired private Clock clock;
  @Autowired private HttpAnswerSteps http;

  private String lastNoticeNpi;
  private String lastNoticeMessage;
  private JsonNode providerBefore;

  // ---- building the history -------------------------------------------------------------------

  @Given("no provider and no history exist")
  public void nothingExists() {
    ProviderRows.wipe(jdbc);
  }

  @Given(
      "enrollment-service registered provider {string} as the individual {string} {string} with Medicaid ID {string}")
  public void registered(String npi, String last, String first, String medicaidId) {
    TestEvents.sendAndAwait(
        json,
        jdbc,
        TestEvents.ENROLLMENT,
        UUID.randomUUID(),
        "ApplicationValidated",
        "enrollment-service",
        npi,
        UUID.randomUUID().toString(),
        TestEvents.individualApplication(npi, medicaidId, last, first));
  }

  @Given("screening-service reported {string} for provider {string}")
  public void screened(String result, String npi) {
    TestEvents.sendAndAwait(
        json,
        jdbc,
        TestEvents.SCREENING,
        UUID.randomUUID(),
        "ScreeningCompleted",
        "screening-service",
        npi,
        UUID.randomUUID().toString(),
        TestEvents.screening(npi, result));
  }

  @Given("risk-service assigned risk tier {string} to provider {string}")
  public void tiered(String tier, String npi) {
    TestEvents.sendAndAwait(
        json,
        jdbc,
        TestEvents.RISK,
        UUID.randomUUID(),
        "RiskTierAssigned",
        "risk-service",
        npi,
        UUID.randomUUID().toString(),
        TestEvents.riskTier(npi, tier));
  }

  @Given("{string} approved provider {string} with correlation id {string}")
  public void approved(String analyst, String npi, String correlationId) throws Exception {
    MvcResult r =
        mvc.perform(
                post("/api/v1/providers/{npi}/approval", npi)
                    .with(PemJwt.user(analyst, null, "ANALYST"))
                    .header("X-Correlation-Id", correlationId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"decision\":\"APPROVE\"}"))
            .andReturn();
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
  }

  @Given(
      "a SYSTEM caller suspended provider {string} with reason {string}, effective date {string} and sanction id {string}")
  public void suspended(String npi, String reason, String effectiveDate, String sanctionId)
      throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("action", "SUSPEND");
    body.put("reason", reason);
    body.put("effectiveDate", effectiveDate);
    body.put("sanctionId", sanctionId);
    MvcResult r =
        mvc.perform(
                post("/api/v1/providers/{npi}/status-changes", npi)
                    .with(PemJwt.system())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body)))
            .andReturn();
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
  }

  /** Writes {@code count} rows straight into the history, one second apart, named ENTRY_1.. */
  @Given("provider {string} has {int} history entries")
  public void historyRows(String npi, int count) {
    Instant start = Instant.parse("2026-11-18T09:00:00Z");
    for (int i = 1; i <= count; i++) {
      jdbc.update(
          "INSERT INTO audit_log (npi, actor, action, before_json, after_json, correlation_id, at)"
              + " VALUES (?, 'seed', ?, NULL, NULL, NULL, ?)",
          npi,
          "ENTRY_" + i,
          Timestamp.from(start.plusSeconds(i)));
    }
  }

  @Given("the fields and version of provider {string} are noted")
  public void noteProvider(String npi) throws Exception {
    providerBefore = provider(npi);
  }

  @When(
      "notification-service sends NoticeSent {string} with notice {string} and template {string} for provider {string}")
  public void noticeSent(String eventId, String noticeId, String template, String npi) {
    lastNoticeNpi = npi;
    lastNoticeMessage =
        TestEvents.envelope(
            json,
            UUID.fromString(eventId),
            "NoticeSent",
            "notification-service",
            npi,
            UUID.randomUUID().toString(),
            TestEvents.noticeSent(npi, UUID.fromString(noticeId), template));
    TestEvents.send(TestEvents.NOTIFICATION, npi, lastNoticeMessage);
    TestEvents.awaitProcessed(jdbc, UUID.fromString(eventId));
  }

  /** The marker has the same key, so the second delivery was handled before it was processed. */
  @When("the same NoticeSent is delivered a second time")
  public void deliveredAgain() {
    TestEvents.send(TestEvents.NOTIFICATION, lastNoticeNpi, lastNoticeMessage);
    UUID marker = UUID.randomUUID();
    TestEvents.sendAndAwait(
        json,
        jdbc,
        TestEvents.NOTIFICATION,
        marker,
        "TestMarker",
        "notification-service",
        lastNoticeNpi,
        UUID.randomUUID().toString(),
        Map.of("npi", lastNoticeNpi));
  }

  // ---- reading the history --------------------------------------------------------------------

  private JsonNode provider(String npi) throws Exception {
    MvcResult r =
        mvc.perform(get("/api/v1/providers/{npi}", npi).with(PemJwt.system())).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString());
  }

  private List<JsonNode> history(String npi) throws Exception {
    MvcResult r =
        mvc.perform(
                get("/api/v1/providers/{npi}/audit", npi)
                    .param("size", "200")
                    .with(PemJwt.supervisor()))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    List<JsonNode> out = new ArrayList<>();
    json.readTree(r.getResponse().getContentAsString()).path("items").forEach(out::add);
    return out;
  }

  private List<JsonNode> items(int answer) throws Exception {
    List<JsonNode> out = new ArrayList<>();
    http.body(answer).path("items").forEach(out::add);
    return out;
  }

  private JsonNode entryWithAction(int answer, String action) throws Exception {
    List<JsonNode> found =
        items(answer).stream().filter(e -> action.equals(e.path("action").asText())).toList();
    assertThat(found).as("entries with action %s", action).hasSize(1);
    return found.get(0);
  }

  @Then("answer {int} lists the history actions {string} oldest first")
  public void actionsInOrder(int answer, String expected) throws Exception {
    assertThat(items(answer).stream().map(e -> e.path("action").asText()).toList())
        .containsExactly(expected.split(",\\s*"));
  }

  @Then("answer {int} lists exactly {int} history entries")
  public void entryCount(int answer, int count) throws Exception {
    assertThat(items(answer)).hasSize(count);
    assertThat(http.body(answer).path("total").asInt()).isEqualTo(count);
  }

  @Then("the {string} entry of answer {int} has these fields:")
  public void entryHasFields(String action, int answer, DataTable table) throws Exception {
    JsonNode e = entryWithAction(answer, action);
    for (List<String> row : table.asLists()) {
      JsonNode v = e.at(row.get(0));
      if ("<absent>".equals(row.get(1))) {
        assertThat(v.isMissingNode() || v.isNull()).as("%s is absent", row.get(0)).isTrue();
      } else {
        assertThat(v.isMissingNode()).as("%s is present", row.get(0)).isFalse();
        assertThat(v.asText()).as(row.get(0)).isEqualTo(row.get(1));
      }
    }
  }

  @Then("the {string} entry of answer {int} has a time and the due date five years after today")
  public void approvalEntry(String action, int answer) throws Exception {
    JsonNode e = entryWithAction(answer, action);
    assertThat(Instant.parse(e.path("at").asText())).isNotNull();
    LocalDate due = LocalDate.parse(e.at("/after/revalidationDueDate").asText());
    LocalDate today = LocalDate.now(clock);
    assertThat(due).isIn(today.plusYears(5), today.minusDays(1).plusYears(5));
  }

  @Then("the history of provider {string} has exactly {int} entry referring to notice {string}")
  public void noticeEntries(String npi, int count, String noticeId) throws Exception {
    List<JsonNode> mine =
        history(npi).stream()
            .filter(e -> noticeId.equals(e.at("/after/notice/noticeId").asText()))
            .toList();
    assertThat(mine).hasSize(count);
    if (count == 1) {
      assertThat(mine.get(0).path("action").asText()).isEqualTo("NOTICE_SENT");
      assertThat(mine.get(0).at("/after/notice/template").asText()).isEqualTo("APPROVED");
    }
  }

  @Then("provider {string} has the same fields and version as noted")
  public void sameProvider(String npi) throws Exception {
    assertThat(provider(npi).toString()).isEqualTo(providerBefore.toString());
  }

  @Then("answer {int} lists the history actions {string} on this page")
  public void pageActions(int answer, String expected) throws Exception {
    assertThat(items(answer).stream().map(e -> e.path("action").asText()).toList())
        .containsExactly(expected.split(",\\s*"));
  }

  @Then("the first {int} entries of answer {int} are unchanged in answer {int}")
  public void earlierUnchanged(int count, int later, int earlier) throws Exception {
    List<JsonNode> before = items(earlier);
    List<JsonNode> after = items(later);
    assertThat(before).hasSizeGreaterThanOrEqualTo(count);
    for (int i = 0; i < count; i++) {
      assertThat(after.get(i).toString()).isEqualTo(before.get(i).toString());
    }
  }

  @Then("answer {int} has {int} entries and the entry {string} is the last one")
  public void lastEntry(int answer, int count, String action) throws Exception {
    List<JsonNode> all = items(answer);
    assertThat(all).hasSize(count);
    assertThat(all.get(all.size() - 1).path("action").asText()).isEqualTo(action);
  }

  @Then("answer {int} allows only GET")
  public void allowsOnlyGet(int answer) {
    String allow = http.answer(answer).getResponse().getHeader("Allow");
    assertThat(allow).isNotNull();
    assertThat(allow).contains("GET").doesNotContain("PUT", "PATCH", "DELETE", "POST");
  }
}
