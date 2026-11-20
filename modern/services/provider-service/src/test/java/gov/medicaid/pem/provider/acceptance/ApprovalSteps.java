package gov.medicaid.pem.provider.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.testsupport.PemContainers;
import gov.medicaid.pem.testsupport.PemJwt;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Steps for MAM-27 (analyst approval or denial). */
public class ApprovalSteps {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  @Autowired private Clock clock;

  private MvcResult result;

  private String dueDate() {
    return LocalDate.now(clock).plusYears(5).toString();
  }

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

  private void decide(String role, String decision, String reason, String npi) throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("decision", decision);
    if (reason != null) {
      body.put("reason", reason);
    }
    MockHttpServletRequestBuilder req =
        post("/api/v1/providers/{npi}/approval", npi)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body));
    RequestPostProcessor t = token(role);
    if (t != null) {
      req = req.with(t);
    }
    result = mvc.perform(req).andReturn();
  }

  @When("{string} sends the decision {string} for provider {string}")
  public void sends(String role, String decision, String npi) throws Exception {
    decide(role, decision, null, npi);
  }

  @When("{string} sends the decision {string} with reason {string} for provider {string}")
  public void sendsWithReason(String role, String decision, String reason, String npi)
      throws Exception {
    decide(role, decision, reason, npi);
  }

  private JsonNode body() throws Exception {
    return json.readTree(result.getResponse().getContentAsString());
  }

  @Then(
      "the decision answer is 200 with status {string} and the service clock date plus 5 years as revalidation due date")
  public void approved(String status) throws Exception {
    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(200);
    assertThat(body().path("enrollStatus").asText()).isEqualTo(status);
    assertThat(body().path("revalidationDueDate").asText()).isEqualTo(dueDate());
  }

  @Then("the decision answer is 200 with status {string} and no revalidation due date")
  public void denied(String status) throws Exception {
    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(200);
    assertThat(body().path("enrollStatus").asText()).isEqualTo(status);
    assertThat(
            body().path("revalidationDueDate").isMissingNode()
                || body().path("revalidationDueDate").isNull())
        .isTrue();
  }

  @Then("the decision answer is {int} problem+json")
  public void problem(int status) throws Exception {
    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(status);
    assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
  }

  @Then("the decision answer is {int}")
  public void status(int status) {
    assertThat(result.getResponse().getStatus()).isEqualTo(status);
  }

  @Then("the decision answer has no risk tier")
  public void noTier() throws Exception {
    assertThat(body().path("riskTier").isMissingNode() || body().path("riskTier").isNull())
        .isTrue();
  }

  private JsonNode read(String npi) throws Exception {
    MvcResult r =
        mvc.perform(get("/api/v1/providers/{npi}", npi).with(PemJwt.system())).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString());
  }

  @Then(
      "reading provider {string} gives status {string} and the service clock date plus 5 years as revalidation due date")
  public void readApproved(String npi, String status) throws Exception {
    JsonNode p = read(npi);
    assertThat(p.path("enrollStatus").asText()).isEqualTo(status);
    assertThat(p.path("revalidationDueDate").asText()).isEqualTo(dueDate());
  }

  @Then("reading provider {string} gives status {string} and no revalidation due date")
  public void readNoDue(String npi, String status) throws Exception {
    JsonNode p = read(npi);
    assertThat(p.path("enrollStatus").asText()).isEqualTo(status);
    assertThat(
            p.path("revalidationDueDate").isMissingNode() || p.path("revalidationDueDate").isNull())
        .isTrue();
  }

  @Then("reading provider {string} gives status {string} and revalidation due date {string}")
  public void readDue(String npi, String status, String due) throws Exception {
    JsonNode p = read(npi);
    assertThat(p.path("enrollStatus").asText()).isEqualTo(status);
    assertThat(p.path("revalidationDueDate").asText()).isEqualTo(due);
  }

  private List<JsonNode> events(String npi) throws Exception {
    List<JsonNode> out = new java.util.ArrayList<>();
    for (String p :
        jdbc.queryForList(
            "SELECT payload::text FROM outbox_event WHERE type = 'ProviderStatusChanged' AND subject = ?",
            String.class,
            npi)) {
      out.add(json.readTree(p));
    }
    return out;
  }

  @Then(
      "{int} ProviderStatusChanged event is written for {string} from {string} to {string} by {string} with the due date, type {string}, name {string}, taxonomy {string} and Medicaid ID {string}")
  public void approvalEvent(
      int count,
      String npi,
      String from,
      String to,
      String actor,
      String type,
      String name,
      String taxonomy,
      String medicaidId)
      throws Exception {
    List<JsonNode> e = events(npi);
    assertThat(e).hasSize(count);
    JsonNode d = e.get(0).path("data");
    assertThat(d.path("npi").asText()).isEqualTo(npi);
    assertThat(d.path("from").asText()).isEqualTo(from);
    assertThat(d.path("to").asText()).isEqualTo(to);
    assertThat(d.path("actor").asText()).isEqualTo(actor);
    assertThat(d.path("revalidationDueDate").asText()).isEqualTo(dueDate());
    assertThat(d.path("providerType").asText()).isEqualTo(type);
    assertThat(d.path("displayName").asText()).isEqualTo(name);
    assertThat(d.path("taxonomy").asText()).isEqualTo(taxonomy);
    assertThat(d.path("medicaidId").asText()).isEqualTo(medicaidId);
  }

  @Then(
      "{int} ProviderStatusChanged event is written for {string} from {string} to {string} with reason {string}")
  public void denialEvent(int count, String npi, String from, String to, String reason)
      throws Exception {
    List<JsonNode> e = events(npi);
    assertThat(e).hasSize(count);
    JsonNode d = e.get(0).path("data");
    assertThat(d.path("from").asText()).isEqualTo(from);
    assertThat(d.path("to").asText()).isEqualTo(to);
    assertThat(d.path("reason").asText()).isEqualTo(reason);
    assertThat(
            d.path("revalidationDueDate").isMissingNode() || d.path("revalidationDueDate").isNull())
        .isTrue();
  }

  @Then("a ProviderStatusChanged event with key {string} reaches topic {string} with to {string}")
  public void onTopic(String key, String topic, String to) throws Exception {
    String expectedId = events(key).get(0).path("id").asText();
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
    JsonNode found = null;
    try (var consumer = new KafkaConsumer<String, String>(cfg)) {
      consumer.subscribe(List.of(topic));
      long end = System.nanoTime() + Duration.ofSeconds(30).toNanos();
      while (System.nanoTime() < end && found == null) {
        for (var rec : consumer.poll(Duration.ofMillis(250))) {
          JsonNode n = json.readTree(rec.value());
          if (key.equals(rec.key()) && n.path("id").asText().equals(expectedId)) {
            found = n;
          }
        }
      }
    }
    assertThat(found).as("ProviderStatusChanged for %s on %s", key, topic).isNotNull();
    assertThat(found.path("data").path("to").asText()).isEqualTo(to);
  }
}
