package gov.medicaid.pem.provider.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.testsupport.PemContainers;
import gov.medicaid.pem.testsupport.PemJwt;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
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
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Steps for MAM-17 (register a validated application as a PENDING, UNSCREENED provider). */
public class RegistrationSteps {

  private static final String ENROLLMENT_TOPIC = "pem.enrollment.events";
  private static final String METRIC = "/actuator/metrics/pem.provider.registrations.skipped";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  @Autowired private Clock clock;

  private final ListAppender<ILoggingEvent> log = new ListAppender<>();
  private double skippedBefore;

  @Before("@MAM-17")
  public void captureLogAndCounter() throws Exception {
    log.start();
    ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).addAppender(log);
    skippedBefore = skipped();
  }

  @After("@MAM-17")
  public void stopCapture() {
    ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).detachAppender(log);
  }

  // ---- sending events as enrollment-service does
  // -------------------------------------------------------------------

  private String envelope(UUID id, String type, String npi, Object data) throws Exception {
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("id", id.toString());
    e.put("type", type);
    e.put("source", "enrollment-service");
    e.put("time", "2026-11-18T10:00:00Z");
    e.put("subject", npi);
    e.put("correlationId", UUID.randomUUID().toString());
    e.put("data", data);
    return json.writeValueAsString(e);
  }

  private void send(UUID id, String type, String npi, Object data) throws Exception {
    Map<String, Object> cfg =
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, PemContainers.KAFKA.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    try (var producer = new KafkaProducer<String, String>(cfg)) {
      producer
          .send(new ProducerRecord<>(ENROLLMENT_TOPIC, npi, envelope(id, type, npi, data)))
          .get();
    }
  }

  private void awaitProcessed(UUID id) throws Exception {
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
    throw new AssertionError("event " + id + " was not processed by provider-service within 45 s");
  }

  private static Map<String, Object> individual(
      String npi,
      String medicaidId,
      String type,
      String taxonomy,
      String last,
      String first,
      String licence,
      String state) {
    Map<String, Object> d = new LinkedHashMap<>();
    d.put("applicationId", UUID.randomUUID().toString());
    d.put("npi", npi);
    d.put("medicaidId", medicaidId);
    d.put("providerType", type);
    d.put("taxonomy", taxonomy);
    d.put(
        "individual",
        Map.of("lastName", last, "firstName", first, "licenseNo", licence, "licenseState", state));
    return d;
  }

  private void consume(Map<String, Object> data) throws Exception {
    UUID id = UUID.randomUUID();
    send(id, "ApplicationValidated", (String) data.get("npi"), data);
    awaitProcessed(id);
  }

  @Given("no provider with NPI {string} exists")
  public void noProvider(String npi) {
    jdbc.update(
        "DELETE FROM provider_owner WHERE provider_id IN (SELECT id FROM provider WHERE npi = ?)",
        npi);
    jdbc.update("DELETE FROM provider WHERE npi = ?", npi);
    jdbc.update("DELETE FROM outbox_event WHERE subject = ?", npi);
  }

  @Given("provider {string} is already registered with status {string} and version {int}")
  public void registered(String npi, String status, int version) {
    noProvider(npi);
    jdbc.update(
        "INSERT INTO provider (public_id, npi, medicaid_id, provider_type, taxonomy, risk_tier, enroll_status, screen_flag,"
            + " effective_date, reval_due_date, last_name, first_name, license_no, license_state, version)"
            + " VALUES (?, ?, 'MCD000111', 'IN', '207Q00000X', 'L', ?, 'P', DATE '2026-01-15', DATE '2031-01-15',"
            + " 'RIVERA', 'MARIA', 'MD12345', 'TX', ?)",
        UUID.randomUUID(),
        npi,
        status,
        version);
  }

  @When(
      "provider-service consumes an ApplicationValidated event for the individual {string} {string} with NPI {string}, Medicaid ID {string}, type {string}, taxonomy {string} and licence {string} {string}")
  public void consumeIndividual(
      String last,
      String first,
      String npi,
      String medicaidId,
      String type,
      String taxonomy,
      String licence,
      String state)
      throws Exception {
    consume(individual(npi, medicaidId, type, taxonomy, last, first, licence, state));
  }

  @When(
      "provider-service consumes an ApplicationValidated event for the organisation {string} {string} owned by {string} with NPI {string}, Medicaid ID {string}, type {string} and taxonomy {string}")
  public void consumeOrganisation(
      String legal,
      String dba,
      String owner,
      String npi,
      String medicaidId,
      String type,
      String taxonomy)
      throws Exception {
    Map<String, Object> d = new LinkedHashMap<>();
    d.put("applicationId", UUID.randomUUID().toString());
    d.put("npi", npi);
    d.put("medicaidId", medicaidId);
    d.put("providerType", type);
    d.put("taxonomy", taxonomy);
    d.put("organization", Map.of("legalName", legal, "dbaName", dba, "ownerNpi", owner));
    consume(d);
  }

  @When("a second ApplicationValidated event for NPI {string} with a new event id is consumed")
  public void second(String npi) throws Exception {
    consume(individual(npi, "MCD000999", "IN", "207Q00000X", "OTHER", "NAME", "MD12345", "TX"));
  }

  @When(
      "enrollment-service publishes ApplicationRejected for NPI {string} with error E104 on taxonomy and ApplicationValidated for NPI {string}, and provider-service has processed both")
  public void rejectedAndValidated(String rejectedNpi, String validNpi) throws Exception {
    UUID rejected = UUID.randomUUID();
    send(
        rejected,
        "ApplicationRejected",
        rejectedNpi,
        Map.of(
            "applicationId", UUID.randomUUID().toString(),
            "npi", rejectedNpi,
            "errors", List.of(Map.of("code", "E104", "field", "taxonomy"))));
    UUID validated = UUID.randomUUID();
    send(
        validated,
        "ApplicationValidated",
        validNpi,
        individual(validNpi, "MCD000111", "IN", "207Q00000X", "RIVERA", "MARIA", "MD12345", "TX"));
    awaitProcessed(rejected);
    awaitProcessed(validated);
  }

  @When("the same ApplicationValidated event for NPI {string} is delivered twice")
  public void twice(String npi) throws Exception {
    UUID id = UUID.randomUUID();
    Map<String, Object> data =
        individual(npi, "MCD000111", "IN", "207Q00000X", "RIVERA", "MARIA", "MD12345", "TX");
    send(id, "ApplicationValidated", npi, data);
    awaitProcessed(id);
    send(id, "ApplicationValidated", npi, data);
    UUID marker = UUID.randomUUID();
    send(
        marker,
        "ApplicationRejected",
        npi,
        Map.of("applicationId", UUID.randomUUID().toString(), "npi", npi, "errors", List.of()));
    awaitProcessed(
        marker); // same key, same partition: the redelivery was handled before the marker
  }

  // ---- reading the result
  // -----------------------------------------------------------------------------------------

  private MvcResult read(String npi, RequestPostProcessor token) throws Exception {
    return mvc.perform(get("/api/v1/providers/{npi}", npi).with(token)).andReturn();
  }

  private JsonNode provider(String npi) throws Exception {
    MvcResult r = read(npi, PemJwt.system());
    assertThat(r.getResponse().getStatus()).as("GET provider %s", npi).isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString());
  }

  @Then(
      "reading provider {string} as SYSTEM answers 200 with status {string}, screen flag {string}, no risk tier and no revalidation due date")
  public void pendingUnscreened(String npi, String status, String flag) throws Exception {
    JsonNode p = provider(npi);
    assertThat(p.path("enrollStatus").asText()).isEqualTo(status);
    assertThat(p.path("screenFlag").asText()).isEqualTo(flag);
    assertThat(p.path("riskTier").isMissingNode() || p.path("riskTier").isNull()).isTrue();
    assertThat(
            p.path("revalidationDueDate").isMissingNode() || p.path("revalidationDueDate").isNull())
        .isTrue();
  }

  @Then(
      "provider {string} has Medicaid ID {string}, type {string}, taxonomy {string} and the individual {string} {string} {string} {string}")
  public void individualFields(
      String npi,
      String medicaidId,
      String type,
      String taxonomy,
      String last,
      String first,
      String licence,
      String state)
      throws Exception {
    JsonNode p = provider(npi);
    assertThat(p.path("npi").asText()).isEqualTo(npi);
    assertThat(p.path("medicaidId").asText()).isEqualTo(medicaidId);
    assertThat(p.path("providerType").asText()).isEqualTo(type);
    assertThat(p.path("taxonomy").asText()).isEqualTo(taxonomy);
    JsonNode i = p.path("individual");
    assertThat(i.path("lastName").asText()).isEqualTo(last);
    assertThat(i.path("firstName").asText()).isEqualTo(first);
    assertThat(i.path("licenseNo").asText()).isEqualTo(licence);
    assertThat(i.path("licenseState").asText()).isEqualTo(state);
  }

  @Then(
      "provider {string} has the organisation {string} {string} owned by {string} and no individual block")
  public void organisationFields(String npi, String legal, String dba, String owner)
      throws Exception {
    JsonNode p = provider(npi);
    assertThat(p.path("organization").path("legalName").asText()).isEqualTo(legal);
    assertThat(p.path("organization").path("dbaName").asText()).isEqualTo(dba);
    assertThat(p.path("organization").path("ownerNpi").asText()).isEqualTo(owner);
    assertThat(p.has("individual")).isFalse();
  }

  @Then("{int} ProviderRegistered event(s) is/are written for NPI {string}")
  public void written(int count, String npi) {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE type = 'ProviderRegistered' AND subject = ?",
                Integer.class,
                npi))
        .isEqualTo(count);
  }

  /** The newest ProviderRegistered record with this key on the topic, read with its key. */
  private JsonNode onTopic(String topic, String key) throws Exception {
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
          if (key.equals(rec.key()) && n.path("type").asText().equals("ProviderRegistered")) {
            found = n;
          }
        }
      }
    }
    assertThat(found).as("ProviderRegistered with key %s on %s", key, topic).isNotNull();
    assertThat(found.path("subject").asText()).isEqualTo(key);
    assertThat(found.path("source").asText()).isEqualTo("provider-service");
    return found.path("data");
  }

  @Then(
      "a ProviderRegistered event with key {string} reaches topic {string} with Medicaid ID {string}, type {string}, taxonomy {string}, status {string}, screen flag {string} and display name {string}")
  public void registeredOnTopic(
      String key,
      String topic,
      String medicaidId,
      String type,
      String taxonomy,
      String status,
      String flag,
      String name)
      throws Exception {
    JsonNode d = onTopic(topic, key);
    assertThat(d.path("npi").asText()).isEqualTo(key);
    assertThat(d.path("medicaidId").asText()).isEqualTo(medicaidId);
    assertThat(d.path("providerType").asText()).isEqualTo(type);
    assertThat(d.path("taxonomy").asText()).isEqualTo(taxonomy);
    assertThat(d.path("enrollStatus").asText()).isEqualTo(status);
    assertThat(d.path("screenFlag").asText()).isEqualTo(flag);
    assertThat(d.path("displayName").asText()).isEqualTo(name);
  }

  @Then(
      "a ProviderRegistered event with key {string} reaches topic {string} with display name {string}")
  public void displayNameOnTopic(String key, String topic, String name) throws Exception {
    assertThat(onTopic(topic, key).path("displayName").asText()).isEqualTo(name);
  }

  @Then("provider {string} still has status {string} and version {int}")
  public void unchanged(String npi, String status, int version) throws Exception {
    JsonNode p = provider(npi);
    assertThat(p.path("enrollStatus").asText()).isEqualTo(status);
    assertThat(p.path("version").asInt()).isEqualTo(version);
    assertThat(p.path("medicaidId").asText()).isEqualTo("MCD000111");
  }

  @Then("the service log contains {string}")
  public void logContains(String text) {
    assertThat(log.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anyMatch(m -> m.contains(text));
  }

  private double skipped() throws Exception {
    MvcResult r = mvc.perform(get(METRIC).with(PemJwt.admin())).andReturn();
    if (r.getResponse().getStatus() == 404) {
      return 0;
    }
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString())
        .path("measurements")
        .get(0)
        .path("value")
        .asDouble();
  }

  @Then("the skipped-registrations counter rose by {int}")
  public void counterRose(int by) throws Exception {
    assertThat(skipped()).isEqualTo(skippedBefore + by);
  }

  @Then("the skipped-registrations counter is unchanged")
  public void counterUnchanged() throws Exception {
    assertThat(skipped()).isEqualTo(skippedBefore);
  }

  @Then("reading provider {string} as ANALYST answers {int}")
  public void readAsAnalyst(String npi, int status) throws Exception {
    assertThat(read(npi, PemJwt.analyst()).getResponse().getStatus()).isEqualTo(status);
  }

  @Then("searching providers by NPI {string} as ANALYST finds {int}")
  public void search(String npi, int total) throws Exception {
    MvcResult r =
        mvc.perform(get("/api/v1/providers").param("npi", npi).with(PemJwt.analyst())).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    JsonNode page = json.readTree(r.getResponse().getContentAsString());
    assertThat(page.path("total").asInt()).isEqualTo(total);
    assertThat(page.path("items")).hasSize(total);
  }

  @Then("exactly {int} provider(s) with NPI {string} exist(s)")
  public void count(int n, String npi) {
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM provider WHERE npi = ?", Integer.class, npi))
        .isEqualTo(n);
  }

  @Then(
      "provider {string} has the service clock date as effective date in ISO form and no revalidation due date")
  public void effectiveDate(String npi) throws Exception {
    JsonNode p = provider(npi);
    assertThat(p.path("effectiveDate").asText())
        .matches("\\d{4}-\\d{2}-\\d{2}")
        .isEqualTo(LocalDate.now(clock).toString());
    assertThat(
            p.path("revalidationDueDate").isMissingNode() || p.path("revalidationDueDate").isNull())
        .isTrue();
  }
}
