package gov.medicaid.pem.notification.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.notification.TestMail;
import gov.medicaid.pem.testsupport.PemContainers;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Steps for MAM-59 (notices by email, read back from a real Mailpit). */
public class NoticeSteps {

  private static final HttpClient HTTP = HttpClient.newHttpClient();

  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  @Autowired private JavaMailSender mail;

  private final ListAppender<ILoggingEvent> log = new ListAppender<>();
  private String lastTopic;
  private String lastKey;
  private String lastEnvelope;
  private UUID lastId;

  @Before("@MAM-59")
  public void start() {
    jdbc.update("DELETE FROM outbox_event");
    jdbc.update("DELETE FROM notice");
    log.start();
    ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).addAppender(log);
  }

  @After("@MAM-59")
  public void stop() {
    ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).detachAppender(log);
  }

  // ---- Mailpit
  // ------------------------------------------------------------------------------------------------------

  @Given("Mailpit is empty")
  public void empty() throws Exception {
    HTTP.send(
        HttpRequest.newBuilder(URI.create(TestMail.api() + "/messages")).DELETE().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private JsonNode messages() throws Exception {
    HttpResponse<String> r =
        HTTP.send(
            HttpRequest.newBuilder(URI.create(TestMail.api() + "/messages")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    return json.readTree(r.body()).path("messages");
  }

  @Given("the {string} email for {string} was already sent and is the only email in Mailpit")
  public void alreadySent(String subject, String npi) throws Exception {
    empty();
    SimpleMailMessage m = new SimpleMailMessage();
    m.setFrom("notices@pem.local");
    m.setTo(npi + "@providers.pem.local");
    m.setSubject(subject);
    m.setText("Synthetic test notice");
    mail.send(m);
    assertThat(messages()).hasSize(1);
  }

  // ---- events
  // -------------------------------------------------------------------------------------------------------

  private void send(
      String topic, String type, String source, String subject, Map<String, Object> data)
      throws Exception {
    lastId = UUID.randomUUID();
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("id", lastId.toString());
    e.put("type", type);
    e.put("source", source);
    e.put("time", "2026-11-18T10:00:00Z");
    e.put("subject", subject);
    e.put("correlationId", UUID.randomUUID().toString());
    e.put("data", data);
    lastTopic = topic;
    lastKey = subject;
    lastEnvelope = json.writeValueAsString(e);
    publish(topic, subject, lastEnvelope);
    awaitProcessed(lastId);
  }

  private static void publish(String topic, String key, String value) throws Exception {
    Map<String, Object> cfg =
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, PemContainers.KAFKA.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    try (var producer = new KafkaProducer<String, String>(cfg)) {
      producer.send(new ProducerRecord<>(topic, key, value)).get();
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
    throw new AssertionError(
        "event " + id + " was not processed by notification-service within 45 s");
  }

  @When(
      "notification-service consumes ProviderStatusChanged for {string} from {string} to {string}")
  public void statusChanged(String npi, String from, String to) throws Exception {
    Map<String, Object> d = new LinkedHashMap<>();
    d.put("npi", npi);
    d.put("from", from);
    d.put("to", to);
    d.put("actor", "analyst1");
    send("pem.provider.events", "ProviderStatusChanged", "provider-service", npi, d);
  }

  @When(
      "notification-service consumes ApplicationRejected for {string} with error {string} on {string}")
  public void rejected(String npi, String code, String field) throws Exception {
    send(
        "pem.enrollment.events",
        "ApplicationRejected",
        "enrollment-service",
        npi,
        Map.of(
            "applicationId",
            UUID.randomUUID().toString(),
            "npi",
            npi,
            "errors",
            List.of(Map.of("code", code, "field", field))));
  }

  @When(
      "notification-service consumes ApplicationRejected without an NPI with error {string} on {string}")
  public void rejectedNoNpi(String code, String field) throws Exception {
    send(
        "pem.enrollment.events",
        "ApplicationRejected",
        "enrollment-service",
        "0000000000",
        Map.of(
            "applicationId",
            UUID.randomUUID().toString(),
            "errors",
            List.of(Map.of("code", code, "field", field))));
  }

  @When("notification-service consumes RevalidationDue for {string} due on {string}")
  public void revalidationDue(String npi, String due) throws Exception {
    send(
        "pem.revalidation.events",
        "RevalidationDue",
        "revalidation-service",
        npi,
        Map.of("caseId", UUID.randomUUID().toString(), "npi", npi, "dueDate", due));
  }

  @When("notification-service consumes ExtractProduced for business date {string} with {int} rows")
  public void extract(String date, int rows) throws Exception {
    send(
        "pem.tmsis.events",
        "ExtractProduced",
        "tmsis-service",
        "0000000000",
        Map.of("runId", UUID.randomUUID().toString(), "businessDate", date, "rowCount", rows));
  }

  @When("the same event is delivered to notification-service again")
  public void redeliver() throws Exception {
    publish(lastTopic, lastKey, lastEnvelope);
    UUID marker = UUID.randomUUID();
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("id", marker.toString());
    e.put("type", "TestMarker");
    e.put("source", "test");
    e.put("time", "2026-11-18T10:00:00Z");
    e.put("subject", lastKey);
    e.put("correlationId", UUID.randomUUID().toString());
    e.put("data", Map.of());
    publish(lastTopic, lastKey, json.writeValueAsString(e));
    awaitProcessed(marker); // same topic and key: the redelivery was handled before the marker
  }

  // ---- checks
  // -------------------------------------------------------------------------------------------------------

  private List<JsonNode> to(String address) throws Exception {
    List<JsonNode> out = new ArrayList<>();
    messages()
        .forEach(
            m ->
                m.path("To")
                    .forEach(
                        t -> {
                          if (t.path("Address").asText().equals(address)) {
                            out.add(m);
                          }
                        }));
    return out;
  }

  @Then("Mailpit holds exactly {int} email(s) to {string} with the subject {string}")
  public void mailWithSubject(int count, String address, String subject) throws Exception {
    List<JsonNode> m = to(address);
    assertThat(m).hasSize(count);
    m.forEach(x -> assertThat(x.path("Subject").asText()).isEqualTo(subject));
  }

  @Then("Mailpit holds exactly {int} email(s) to {string} with the subject of template {string}")
  public void mailWithTemplate(int count, String address, String template) throws Exception {
    String subject =
        jdbc.queryForObject(
            "SELECT subject FROM notice_template WHERE code = ?", String.class, template);
    mailWithSubject(count, address, subject);
  }

  @Then("Mailpit holds {int} email(s) in total")
  public void total(int count) throws Exception {
    assertThat(messages()).hasSize(count);
  }

  @Then("{int} NoticeSent event(s) with template {string} is/are written for {string}")
  public void noticeSent(int count, String template, String npi) throws Exception {
    List<String> payloads =
        jdbc.queryForList(
            "SELECT payload::text FROM outbox_event WHERE type = 'NoticeSent' AND subject = ?",
            String.class,
            npi);
    assertThat(payloads).hasSize(count);
    for (String p : payloads) {
      JsonNode d = json.readTree(p).path("data");
      assertThat(d.path("template").asText()).isEqualTo(template);
      assertThat(d.path("npi").asText()).isEqualTo(npi);
      assertThat(d.path("noticeId").asText()).isNotBlank();
    }
  }

  @Then("{int} NoticeSent events are written for {string}")
  public void noticeSentCount(int count, String npi) {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE type = 'NoticeSent' AND subject = ?",
                Integer.class,
                npi))
        .isEqualTo(count);
  }

  @Then("no NoticeSent event is written at all")
  public void noNotice() {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE type = 'NoticeSent'", Integer.class))
        .isZero();
  }

  @Then("an information log line names the event id")
  public void infoLog() {
    assertThat(List.copyOf(log.list))
        .anyMatch(
            e -> e.getLevel() == Level.INFO && e.getFormattedMessage().contains(lastId.toString()));
  }
}
