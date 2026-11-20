package gov.medicaid.pem.risk.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.kit.web.CorrelationId;
import gov.medicaid.pem.risk.IntegrationTest;
import gov.medicaid.pem.testsupport.PemContainers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Code review of risk-service, round 1, MUST-FIX 1 (ScreeningEventsListener): the listener copied
 * the raw {@code correlationId} of the consumed envelope into the MDC. Nothing enforces a format on
 * that string, so a crafted id (CR/LF, longer than 64 characters, an empty string) overwrote the id
 * that the kit's CorrelationIdRecordInterceptor had already made safe, and then went into the
 * RiskTierAssigned envelope (OutboxWriter reads the MDC), into the failure log line, and the
 * listener removed the interceptor's MDC entry before the interceptor's own afterRecord. The
 * correlation id must be safe to log and forward (CorrelationId.sanitize: 1 to 64 characters of
 * letters, digits and . _ : -), a UUID must be kept as it is, and the listener must not disturb the
 * MDC that the interceptor owns. Cross-reference: MAM-36 AC-4 (the id follows the events). Added
 * under Q-097 C, additive: the recorded MAM-35 tests are untouched.
 *
 * <p>Two ways in: through Kafka (the real interceptor and listener) and by calling the listener
 * directly with the MDC set the way the interceptor sets it, {@code sanitize(raw id of the
 * message)}, which is what makes the log and MDC assertions deterministic. A failing event is made
 * with a taxonomy longer than the 10 characters of risk_assessment.taxonomy (a real database error
 * at the store step of AssessRisk, as in MAM-35 AC-8).
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class ScreeningEventsCorrelationIdTest extends IntegrationTest {

  /** What CorrelationId.sanitize accepts: safe to log and to forward. */
  private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

  private static final String NPI_E2E = "1003000771";
  private static final String NPI_DIRECT = "1003000772";
  private static final String TAXONOMY = "207Q00000X";
  private static final String TAXONOMY_TOO_LONG = "207Q00000X999";

  @Autowired ScreeningEventsListener listener;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  private final Logger listenerLog =
      (Logger) LoggerFactory.getLogger(ScreeningEventsListener.class);
  private final Capture capture = new Capture();
  private final List<UUID> eventIds = new ArrayList<>();

  /**
   * A log line as it was when written: the MDC is thread-local, so it is copied at once; the thread
   * tells the lines of the test's own calls from those of the Kafka consumer thread.
   */
  record Logged(String level, String thread, String message, Map<String, String> mdc) {}

  static final class Capture extends AppenderBase<ILoggingEvent> {
    final List<Logged> lines = new CopyOnWriteArrayList<>();

    @Override
    protected void append(ILoggingEvent event) {
      lines.add(
          new Logged(
              event.getLevel().toString(),
              event.getThreadName(),
              event.getFormattedMessage(),
              new HashMap<>(event.getMDCPropertyMap())));
    }
  }

  @BeforeEach
  void startCapture() {
    MDC.clear();
    capture.setContext(listenerLog.getLoggerContext());
    capture.start();
    listenerLog.addAppender(capture);
  }

  @AfterEach
  void cleanUp() {
    listenerLog.detachAppender(capture);
    capture.stop();
    MDC.clear();
    for (String npi : List.of(NPI_E2E, NPI_DIRECT)) {
      jdbc.update("DELETE FROM outbox_event WHERE subject = ?", npi);
      jdbc.update("DELETE FROM risk_assessment WHERE npi = ?", npi);
    }
    for (UUID id : eventIds) {
      jdbc.update("DELETE FROM processed_event WHERE event_id = ?", id);
    }
  }

  /** Correlation ids that are not safe to log or forward, each with a name for the report. */
  static Stream<Arguments> crafted() {
    return Stream.of(
        Arguments.of(
            "CR/LF then a forged JSON log line",
            "7f1c0e52-0d5b-4d57-9b1e-2f6a3c9d8e10\r\n"
                + "{\"@timestamp\":\"2026-01-15T00:00:00Z\",\"log.level\":\"ERROR\",\"message\":\"FORGED\"}"),
        Arguments.of("a bare LF then a forged line", "abc\nINFO FORGED line"),
        Arguments.of("an ANSI escape and a tab", "id\u001b[31m\tFORGED"),
        Arguments.of("65 characters (one over the limit)", "a".repeat(65)),
        Arguments.of("200 characters", "x".repeat(200)),
        Arguments.of("a path and a quote", "../../etc/x\"y"),
        Arguments.of("an empty string", ""));
  }

  /** The crafted ids that carry control characters (log forging). */
  static Stream<Arguments> forging() {
    return crafted().filter(a -> ((String) a.get()[1]).contains("FORGED"));
  }

  /** Every crafted id and a UUID. */
  static Stream<Arguments> craftedAndUuid() {
    return Stream.concat(
        crafted(), Stream.of(Arguments.of("a UUID", UUID.randomUUID().toString())));
  }

  private String envelope(UUID id, String npi, String taxonomy, String correlationId)
      throws Exception {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("screeningId", UUID.randomUUID().toString());
    data.put("npi", npi);
    data.put("reason", "INITIAL");
    data.put("result", "PASS");
    data.put("taxonomy", taxonomy);
    data.put("sources", List.of());
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("id", id.toString());
    e.put("type", "ScreeningCompleted");
    e.put("source", "screening-service");
    e.put("time", "2026-01-15T10:00:00Z");
    e.put("subject", npi);
    e.put("correlationId", correlationId);
    e.put("data", data);
    eventIds.add(id);
    return json.writeValueAsString(e);
  }

  // ---- through Kafka: the real interceptor and the real listener ----

  private static void publish(String key, String value) throws Exception {
    Map<String, Object> cfg =
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
            PemContainers.KAFKA.getBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
            StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
            StringSerializer.class,
            ProducerConfig.MAX_BLOCK_MS_CONFIG,
            20_000,
            ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,
            10_000,
            ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,
            20_000);
    try (var producer = new KafkaProducer<String, String>(cfg)) {
      producer
          .send(new ProducerRecord<>("pem.screening.events", key, value))
          .get(30, TimeUnit.SECONDS);
    }
  }

  private void awaitProcessed(UUID id) throws InterruptedException {
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
    throw new AssertionError("event " + id + " was not processed by risk-service within 45 s");
  }

  private List<String> publishedCorrelationIds(String npi) {
    return jdbc.queryForList(
        "SELECT payload->>'correlationId' FROM outbox_event WHERE type = 'RiskTierAssigned' AND subject = ?",
        String.class,
        npi);
  }

  @ParameterizedTest(
      name =
          "[MAM-35 AC-8] a ScreeningCompleted with {0} as correlation id: the RiskTierAssigned envelope carries a safe id")
  @MethodSource("crafted")
  void publishedEventCarriesASafeId(String what, String raw) throws Exception {
    UUID id = UUID.randomUUID();
    publish(NPI_E2E, envelope(id, NPI_E2E, TAXONOMY, raw));
    awaitProcessed(id);

    List<String> ids = publishedCorrelationIds(NPI_E2E);

    assertThat(ids).hasSize(1);
    assertThat(ids.get(0)).matches(SAFE).isNotEqualTo(raw);
  }

  @Test
  @DisplayName(
      "[MAM-35 AC-8] a ScreeningCompleted with a UUID as correlation id: the RiskTierAssigned envelope keeps it")
  void publishedEventKeepsAUuid() throws Exception {
    String uuid = UUID.randomUUID().toString();
    UUID id = UUID.randomUUID();
    publish(NPI_E2E, envelope(id, NPI_E2E, TAXONOMY, uuid));
    awaitProcessed(id);

    assertThat(publishedCorrelationIds(NPI_E2E)).containsExactly(uuid);
  }

  // ---- the listener called directly, the MDC set the way the kit's interceptor sets it ----

  /** The MDC that CorrelationIdRecordInterceptor.intercept sets for a message with this raw id. */
  private static String interceptorSets(String raw) {
    String safe = CorrelationId.sanitize(raw);
    MDC.put(CorrelationId.MDC_KEY, safe);
    return safe;
  }

  /** Handles a failing event (a database error at the store step) and returns its error line. */
  private Logged failingEvent(String raw) throws Exception {
    String message = envelope(UUID.randomUUID(), NPI_DIRECT, TAXONOMY_TOO_LONG, raw);
    assertThatThrownBy(() -> listener.onEvent(message))
        .as("the failure must reach the Kafka error handler (retry, then dead letter)")
        .isInstanceOf(DataAccessException.class);
    String me = Thread.currentThread().getName();
    List<Logged> errors =
        capture.lines.stream()
            .filter(l -> "ERROR".equals(l.level()) && me.equals(l.thread()))
            .toList();
    assertThat(errors).as("one error line for the failed event").hasSize(1);
    return errors.get(0);
  }

  @ParameterizedTest(
      name =
          "[MAM-35 AC-8] the failure log of an event with {0} as correlation id does not hold the raw value (no log forging)")
  @MethodSource("forging")
  void failureLogHoldsNoForgedText(String what, String raw) throws Exception {
    interceptorSets(raw);

    Logged line = failingEvent(raw);

    assertThat(line.message()).doesNotContain(raw).doesNotContain("FORGED").doesNotContain("\r");
  }

  @ParameterizedTest(
      name =
          "[MAM-35 AC-8] the failure log of an event with {0} as correlation id carries a safe id, the one of the MDC")
  @MethodSource("crafted")
  void failureLogCarriesASafeId(String what, String raw) throws Exception {
    interceptorSets(raw);

    Logged line = failingEvent(raw);

    String logged = line.mdc().get(CorrelationId.MDC_KEY);
    assertThat(logged).matches(SAFE).isNotEqualTo(raw);
    assertThat(line.message()).contains(logged);
  }

  @Test
  @DisplayName(
      "[MAM-35 AC-8] the failure log of an event with a UUID as correlation id carries that UUID, in the line and in the MDC")
  void failureLogKeepsAUuid() throws Exception {
    String uuid = UUID.randomUUID().toString();
    interceptorSets(uuid);

    Logged line = failingEvent(uuid);

    assertThat(line.mdc()).containsEntry(CorrelationId.MDC_KEY, uuid);
    assertThat(line.message()).contains(uuid);
  }

  @ParameterizedTest(
      name =
          "[MAM-35 AC-8] after handling an event with {0} as correlation id the listener leaves the id set by the interceptor in the MDC")
  @MethodSource("craftedAndUuid")
  void mdcIsKeptAfterAHandledEvent(String what, String raw) throws Exception {
    String set = interceptorSets(raw);

    listener.onEvent(envelope(UUID.randomUUID(), NPI_DIRECT, TAXONOMY, raw));

    assertThat(MDC.get(CorrelationId.MDC_KEY)).isEqualTo(set);
  }

  @ParameterizedTest(
      name =
          "[MAM-35 AC-8] after a failed event with {0} as correlation id the listener leaves the id set by the interceptor in the MDC")
  @MethodSource("craftedAndUuid")
  void mdcIsKeptAfterAFailedEvent(String what, String raw) throws Exception {
    String set = interceptorSets(raw);

    failingEvent(raw);

    assertThat(MDC.get(CorrelationId.MDC_KEY)).isEqualTo(set);
  }
}
