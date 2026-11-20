package gov.medicaid.pem.notification.acceptance;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import gov.medicaid.pem.notification.TestMail;
import gov.medicaid.pem.testsupport.PemContainers;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.net.ServerSocket;
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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Steps for MAM-58 (sanction notices TRM, SUS, RST from SanctionApplied, read back from a real
 * Mailpit). The mail outage is injected by pointing the application's mail sender at a closed port
 * for the scenario; it is put back afterwards.
 */
public class SanctionNoticeSteps {

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final String TOPIC = "pem.sanctions.events";

  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  @Autowired private JavaMailSender mail;

  private final ListAppender<ILoggingEvent> log = new ListAppender<>();
  private final List<UUID> eventIds = new ArrayList<>();
  private String lastKey;
  private String lastEnvelope;
  private Integer mailPort;
  private String removedCode;
  private String removedSubject;
  private String removedBody;
  private UUID publishedEventId;

  @Before("@MAM-58")
  public void start() {
    jdbc.update("DELETE FROM outbox_event");
    jdbc.update("DELETE FROM notice");
    log.start();
    ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).addAppender(log);
  }

  @After("@MAM-58")
  public void stop() {
    ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).detachAppender(log);
    if (mailPort != null) {
      ((JavaMailSenderImpl) mail).setPort(mailPort);
    }
    if (removedCode != null) {
      jdbc.update(
          "INSERT INTO notice_template (code, subject, body) VALUES (?, ?, ?) ON CONFLICT (code) DO NOTHING",
          removedCode,
          removedSubject,
          removedBody);
    }
  }

  // ---- given -----------------------------------------------------------------------------------

  @Given("the sanction notice templates TRM, SUS and RST are seeded")
  public void seeded() {
    assertThat(
            jdbc.queryForList(
                "SELECT code FROM notice_template WHERE code IN ('TRM', 'SUS', 'RST')",
                String.class))
        .containsExactlyInAnyOrder("TRM", "SUS", "RST");
  }

  @Given("the mail server cannot be reached")
  public void mailDown() throws Exception {
    JavaMailSenderImpl sender = (JavaMailSenderImpl) mail;
    int closed;
    try (ServerSocket s = new ServerSocket(0)) {
      closed = s.getLocalPort();
    }
    mailPort = sender.getPort();
    sender.setPort(closed);
  }

  @Given("the notice template {string} has been removed")
  public void removeTemplate(String code) {
    Map<String, Object> row =
        jdbc.queryForMap("SELECT subject, body FROM notice_template WHERE code = ?", code);
    removedCode = code;
    removedSubject = (String) row.get("subject");
    removedBody = (String) row.get("body");
    jdbc.update("DELETE FROM notice_template WHERE code = ?", code);
  }

  // ---- events ----------------------------------------------------------------------------------

  private static void publish(String key, String value) throws Exception {
    Map<String, Object> cfg =
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, PemContainers.KAFKA.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    try (var producer = new KafkaProducer<String, String>(cfg)) {
      producer.send(new ProducerRecord<>(TOPIC, key, value)).get();
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

  private String envelope(UUID id, String type, String subject, Map<String, Object> data)
      throws Exception {
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("id", id.toString());
    e.put("type", type);
    e.put("source", "sanctions-service");
    e.put("time", "2026-11-19T10:00:00Z");
    e.put("subject", subject);
    e.put("correlationId", UUID.randomUUID().toString());
    e.put("data", data);
    return json.writeValueAsString(e);
  }

  @When(
      "notification-service consumes SanctionApplied for {string} with action {string}, reason {string} and effective date {string}")
  public void sanctionApplied(String npi, String action, String reason, String effectiveDate)
      throws Exception {
    UUID id = UUID.randomUUID();
    Map<String, Object> d = new LinkedHashMap<>();
    d.put("sanctionId", UUID.randomUUID().toString());
    d.put("npi", npi);
    d.put("action", action);
    d.put("reason", reason);
    d.put("effectiveDate", effectiveDate);
    lastKey = npi;
    lastEnvelope = envelope(id, "SanctionApplied", npi, d);
    eventIds.add(id);
    publish(npi, lastEnvelope);
    awaitProcessed(id);
  }

  @When("the same SanctionApplied event is delivered again")
  public void redeliver() throws Exception {
    publish(lastKey, lastEnvelope);
    UUID marker = UUID.randomUUID();
    publish(lastKey, envelope(marker, "TestMarker", lastKey, Map.of()));
    awaitProcessed(marker); // same topic and key: the redelivery was handled before the marker
  }

  @When("the outbox poller has published the NoticeSent event of {string}")
  public void published(String npi) throws Exception {
    List<UUID> ids =
        jdbc.queryForList(
            "SELECT id FROM outbox_event WHERE type = 'NoticeSent' AND subject = ?",
            UUID.class,
            npi);
    assertThat(ids).hasSize(1);
    publishedEventId = ids.get(0);
    long end = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (System.nanoTime() < end) {
      Integer n =
          jdbc.queryForObject(
              "SELECT count(*) FROM outbox_event WHERE id = ? AND published_at IS NOT NULL",
              Integer.class,
              publishedEventId);
      if (n != null && n > 0) {
        return;
      }
      Thread.sleep(200);
    }
    throw new AssertionError("NoticeSent " + publishedEventId + " was not published within 30 s");
  }

  // ---- checks ----------------------------------------------------------------------------------

  private JsonNode messagesTo(String address) throws Exception {
    HttpResponse<String> r =
        HTTP.send(
            HttpRequest.newBuilder(URI.create(TestMail.api() + "/messages")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    List<JsonNode> out = new ArrayList<>();
    json.readTree(r.body())
        .path("messages")
        .forEach(
            m ->
                m.path("To")
                    .forEach(
                        t -> {
                          if (t.path("Address").asText().equals(address)) {
                            out.add(m);
                          }
                        }));
    return json.valueToTree(out);
  }

  @Then("the email to {string} has the body of template {string} filled in with NPI {string}")
  public void body(String address, String template, String npi) throws Exception {
    JsonNode m = messagesTo(address);
    assertThat(m).hasSize(1);
    HttpResponse<String> r =
        HTTP.send(
            HttpRequest.newBuilder(
                    URI.create(TestMail.api() + "/message/" + m.get(0).path("ID").asText()))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    String text = json.readTree(r.body()).path("Text").asText().replace("\r\n", "\n").trim();
    String body =
        jdbc.queryForObject(
            "SELECT body FROM notice_template WHERE code = ?", String.class, template);
    assertThat(text).isEqualTo(body.replace("{npi}", npi).trim()).contains(npi);
  }

  private List<ILoggingEvent> lines(Level level) {
    return List.copyOf(log.list).stream().filter(e -> e.getLevel() == level).toList();
  }

  @Then("an error log line names the first SanctionApplied event id and the action {string}")
  public void errorLine(String action) {
    String id = eventIds.get(0).toString();
    assertThat(lines(Level.ERROR))
        .extracting(ILoggingEvent::getFormattedMessage)
        .anyMatch(m -> m.contains(id) && m.contains("action " + action));
  }

  private int warningIndex(String template, String npi) {
    List<ILoggingEvent> all = List.copyOf(log.list);
    for (int i = 0; i < all.size(); i++) {
      ILoggingEvent e = all.get(i);
      String m = e.getFormattedMessage();
      if (e.getLevel() == Level.WARN
          && m.contains(template)
          && m.contains(npi)
          && m.contains("FAILED")
          && m.contains("cause: ")
          && !m.substring(m.indexOf("cause: ") + 7).isBlank()) {
        return i;
      }
    }
    return -1;
  }

  @Then("a warning log line names template {string}, NPI {string}, status FAILED and a cause")
  public void warning(String template, String npi) {
    assertThat(warningIndex(template, npi))
        .as("WARN line with %s, %s, FAILED and a cause in %s", template, npi, log.list)
        .isNotNegative();
  }

  @Then("the warning for {string} of {string} comes before the warning for {string} of {string}")
  public void order(String first, String firstNpi, String second, String secondNpi) {
    int a = warningIndex(first, firstNpi);
    int b = warningIndex(second, secondNpi);
    assertThat(a).isNotNegative();
    assertThat(a).isLessThan(b);
  }

  @Then("a warning log line contains {string} and status FAILED")
  public void warningContains(String text) {
    assertThat(lines(Level.WARN))
        .extracting(ILoggingEvent::getFormattedMessage)
        .anyMatch(m -> m.contains(text) && m.contains("FAILED"));
  }

  @Then("the notice {string} for {string} is recorded with status FAILED")
  public void failedNotice(String template, String npi) {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM notice WHERE npi = ? AND template_code = ? AND status = 'FAILED'",
                Integer.class,
                npi,
                template))
        .isEqualTo(1);
  }

  @Then("notification-service has no client that could change or roll back a provider status")
  public void noProviderClient() {
    noClasses()
        .that()
        .resideInAPackage("gov.medicaid.pem.notification..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework.web.client..",
            "org.springframework.web.reactive.function.client..",
            "gov.medicaid.pem.kit.client..",
            "java.net.http..")
        .check(
            new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("gov.medicaid.pem.notification"));
  }

  @Then(
      "exactly {int} NoticeSent event(s) of this notice is/are on topic {string} with key {string}, npi {string}, template {string} and the stored noticeId")
  public void onTopic(int count, String topic, String key, String npi, String template)
      throws Exception {
    UUID noticeId =
        jdbc.queryForObject(
            "SELECT public_id FROM notice WHERE npi = ? AND template_code = ? AND status = 'SENT'",
            UUID.class,
            npi,
            template);
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
    List<JsonNode> found = new ArrayList<>();
    List<String> keys = new ArrayList<>();
    try (var consumer = new KafkaConsumer<String, String>(cfg)) {
      consumer.subscribe(List.of(topic));
      long end = System.nanoTime() + Duration.ofSeconds(30).toNanos();
      long settle = Long.MAX_VALUE;
      while (System.nanoTime() < Math.min(end, settle)) {
        for (var rec : consumer.poll(Duration.ofMillis(250))) {
          JsonNode n = json.readTree(rec.value());
          if (n.path("id").asText().equals(publishedEventId.toString())) {
            found.add(n);
            keys.add(rec.key());
            settle = System.nanoTime() + Duration.ofSeconds(2).toNanos();
          }
        }
      }
    }
    assertThat(found).hasSize(count);
    assertThat(keys).containsOnly(key);
    JsonNode e = found.get(0);
    assertThat(e.path("type").asText()).isEqualTo("NoticeSent");
    assertThat(e.path("subject").asText()).isEqualTo(key);
    assertThat(e.path("data").path("npi").asText()).isEqualTo(npi);
    assertThat(e.path("data").path("template").asText()).isEqualTo(template);
    assertThat(e.path("data").path("noticeId").asText()).isEqualTo(noticeId.toString());
  }
}
