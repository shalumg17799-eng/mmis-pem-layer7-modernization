package gov.medicaid.pem.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.testsupport.PemContainers;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.DockerClientFactory;

/**
 * MAM-36 on provider-service: health with database and Kafka (AC-1), and the correlation id of a
 * consumed event carried into the log lines and the events it causes (AC-4). The database outage is
 * injected by pausing the test's own Postgres container for the length of one test.
 */
class ObservabilityTest extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired HealthEndpoint health;

  /** Keeps level, message and the MDC correlation id as they were in the logging thread. */
  static final class Captured extends AppenderBase<ILoggingEvent> {
    record Line(Level level, String message, String correlationId) {}

    final List<Line> lines = new CopyOnWriteArrayList<>();

    @Override
    protected void append(ILoggingEvent e) {
      lines.add(
          new Line(
              e.getLevel(), e.getFormattedMessage(), e.getMDCPropertyMap().get("correlationId")));
    }
  }

  private JsonNode healthAnswer(int expectedStatus) throws Exception {
    MvcResult r = mvc.perform(get("/actuator/health")).andReturn();
    assertThat(r.getResponse().getStatus())
        .as(r.getResponse().getContentAsString())
        .isEqualTo(expectedStatus);
    return json.readTree(r.getResponse().getContentAsString());
  }

  @Test
  @DisplayName(
      "[MAM-36 AC-1] health answers 200 UP without a token, with the database and Kafka components UP")
  void healthUp() throws Exception {
    assertThat(healthAnswer(200).path("status").asText()).isEqualTo("UP");
    HealthComponent db = health.healthForPath("db");
    HealthComponent kafka = health.healthForPath("kafka");
    assertThat(db).as("db health component").isNotNull();
    assertThat(db.getStatus()).isEqualTo(Status.UP);
    assertThat(kafka).as("kafka health component").isNotNull();
    assertThat(kafka.getStatus()).isEqualTo(Status.UP);
  }

  @Test
  @DisplayName(
      "[MAM-36 AC-1] while the database is stopped health answers 503 DOWN, and 200 UP again once it is back")
  void healthFollowsTheDatabase() throws Exception {
    var docker = DockerClientFactory.instance().client();
    String postgres = PemContainers.POSTGRES.getContainerId();
    ExecutorService probe = Executors.newSingleThreadExecutor();
    docker.pauseContainerCmd(postgres).exec();
    try {
      // Q-097 C, 2026-11-20: the pool must validate its idle connections (it skips a connection
      // used
      // less than 500 ms ago), and the answer is waited for with a limit, so a frozen database can
      // never hang the build; the assertion is unchanged
      Thread.sleep(1500);
      Future<JsonNode> answer = probe.submit(() -> healthAnswer(503));
      JsonNode down;
      try {
        down = answer.get(90, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        throw new AssertionError("no health answer within 90 s while the database was stopped", e);
      } catch (ExecutionException e) {
        if (e.getCause() instanceof AssertionError a) {
          throw a;
        }
        throw e;
      }
      assertThat(down.path("status").asText()).isEqualTo("DOWN");
    } finally {
      docker.unpauseContainerCmd(postgres).exec();
      probe.shutdownNow();
    }
    long end = System.nanoTime() + Duration.ofSeconds(60).toNanos();
    int status = 0;
    while (System.nanoTime() < end && status != 200) {
      status = mvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus();
      if (status != 200) {
        Thread.sleep(500);
      }
    }
    assertThat(healthAnswer(200).path("status").asText()).isEqualTo("UP");
  }

  private void publish(UUID id, String npi, String correlationId) throws Exception {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("applicationId", UUID.randomUUID().toString());
    data.put("npi", npi);
    data.put("medicaidId", "MCD000361");
    data.put("providerType", "IN");
    data.put("taxonomy", "207Q00000X");
    data.put(
        "individual",
        Map.of(
            "lastName",
            "RIVERA",
            "firstName",
            "MARIA",
            "licenseNo",
            "MD12345",
            "licenseState",
            "TX"));
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("id", id.toString());
    e.put("type", "ApplicationValidated");
    e.put("source", "enrollment-service");
    e.put("time", "2026-11-19T10:00:00Z");
    e.put("subject", npi);
    e.put("correlationId", correlationId);
    e.put("data", data);
    Map<String, Object> cfg =
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, PemContainers.KAFKA.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    try (var producer = new KafkaProducer<String, String>(cfg)) {
      producer
          .send(new ProducerRecord<>("pem.enrollment.events", npi, json.writeValueAsString(e)))
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
    throw new AssertionError("event " + id + " was not processed within 45 s");
  }

  @Test
  @DisplayName(
      "[MAM-36 AC-4] the correlationId of a consumed ApplicationValidated is in the registration log line and in ProviderRegistered")
  void correlationIdFollowsTheEvent() throws Exception {
    String npi = "1003000361";
    String correlationId = "mam36-corr-p1";
    jdbc.update(
        "DELETE FROM provider_owner WHERE provider_id IN (SELECT id FROM provider WHERE npi = ?)",
        npi);
    jdbc.update("DELETE FROM provider WHERE npi = ?", npi);
    jdbc.update("DELETE FROM outbox_event WHERE subject = ?", npi);
    Captured log = new Captured();
    log.start();
    Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    root.addAppender(log);
    try {
      UUID id = UUID.randomUUID();
      publish(id, npi, correlationId);
      awaitProcessed(id);
    } finally {
      root.detachAppender(log);
    }
    List<String> envelopes =
        jdbc.queryForList(
            "SELECT payload::text FROM outbox_event WHERE type = 'ProviderRegistered' AND subject = ?",
            String.class,
            npi);
    assertThat(envelopes).hasSize(1);
    assertThat(json.readTree(envelopes.get(0)).path("correlationId").asText())
        .isEqualTo(correlationId);
    assertThat(log.lines)
        .anyMatch(
            l ->
                l.level() == Level.INFO
                    && l.message().contains(npi)
                    && correlationId.equals(l.correlationId()));
  }
}
