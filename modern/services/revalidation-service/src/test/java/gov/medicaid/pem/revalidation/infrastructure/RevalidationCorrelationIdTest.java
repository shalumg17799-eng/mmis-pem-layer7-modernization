package gov.medicaid.pem.revalidation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.revalidation.IntegrationTest;
import gov.medicaid.pem.revalidation.RevalidationFixtures;
import gov.medicaid.pem.testsupport.EventProbe;
import gov.medicaid.pem.testsupport.PemContainers;
import gov.medicaid.pem.testsupport.PemJwt;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * A correlation id that comes from outside never reaches the log or an outgoing event unsanitised
 * (lesson g of the earlier reviews; java-microservices 11): the X-Correlation-Id header of the
 * sweep call, and the correlationId of a ScreeningCompleted that closes a case. A crafted id (CR/LF
 * then a forged log line, an ANSI escape, more than 64 characters, an empty string, a path) is
 * replaced by a generated one; a UUID is kept. The id the service logs and the id of the
 * RevalidationDue it writes are safe to log and to forward: 1 to 64 characters of letters, digits
 * and . _ : -.
 */
@Timeout(240)
class RevalidationCorrelationIdTest extends IntegrationTest {

  static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._:-]{1,64}");
  static final String NPI = "1003000222";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  private final Logger logger = (Logger) LoggerFactory.getLogger("gov.medicaid.pem");
  private final Capture capture = new Capture();

  /** A log line as it was written: the MDC is thread-local, so it is copied at once. */
  record Logged(String level, String message, Map<String, String> mdc) {}

  static final class Capture extends AppenderBase<ILoggingEvent> {
    final List<Logged> lines = new CopyOnWriteArrayList<>();

    @Override
    protected void append(ILoggingEvent event) {
      lines.add(
          new Logged(
              event.getLevel().toString(),
              event.getFormattedMessage(),
              new HashMap<>(event.getMDCPropertyMap())));
    }
  }

  @BeforeEach
  void startCapture() {
    RevalidationFixtures.reset(jdbc);
    capture.setContext(logger.getLoggerContext());
    capture.start();
    logger.addAppender(capture);
  }

  @AfterEach
  void cleanUp() {
    logger.detachAppender(capture);
    capture.stop();
    RevalidationFixtures.reset(jdbc);
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

  // ---- the header of the sweep call ------------------------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-45 AC-1] an X-Correlation-Id header with {0}: the response carries a safe id, not the raw value")
  @MethodSource("crafted")
  void responseHeaderIsSafe(String what, String raw) throws Exception {
    MvcResult r =
        mvc.perform(
                MockMvcRequestBuilders.post(RevalidationFixtures.SWEEPS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Correlation-Id", raw)
                    .content("{\"businessDate\":\"2026-03-31\"}")
                    .with(PemJwt.admin()))
            .andReturn();
    RevalidationFixtures.awaitNoRunningJob(jdbc);

    String echoed = r.getResponse().getHeader("X-Correlation-Id");
    assertThat(echoed).matches(SAFE).isNotEqualTo(raw);
  }

  @ParameterizedTest(
      name =
          "[MAM-45 AC-4] an X-Correlation-Id header with {0}: the RevalidationDue of the sweep carries a safe id, not the raw value")
  @MethodSource("crafted")
  void outgoingEventIsSafe(String what, String raw) throws Exception {
    RevalidationFixtures.track(jdbc, NPI, "A", LocalDate.of(2026, 2, 28));
    mvc.perform(
            MockMvcRequestBuilders.post(RevalidationFixtures.SWEEPS)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-Id", raw)
                .content("{\"businessDate\":\"2026-03-31\"}")
                .with(PemJwt.admin()))
        .andReturn();
    RevalidationFixtures.awaitNoRunningJob(jdbc);

    List<JsonNode> events = RevalidationFixtures.outbox(jdbc, "RevalidationDue");

    assertThat(events).hasSize(1);
    String id = events.get(0).path("correlationId").asText();
    assertThat(id).matches(SAFE).isNotEqualTo(raw);
    assertThat(capture.lines).noneMatch(l -> l.message().contains("FORGED"));
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-4] a UUID in the X-Correlation-Id header is echoed on the response and the event is a UUID too")
  void uuidHeaderIsKept() throws Exception {
    String uuid = UUID.randomUUID().toString();
    RevalidationFixtures.track(jdbc, NPI, "A", LocalDate.of(2026, 2, 28));

    MvcResult r =
        mvc.perform(
                MockMvcRequestBuilders.post(RevalidationFixtures.SWEEPS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Correlation-Id", uuid)
                    .content("{\"businessDate\":\"2026-03-31\"}")
                    .with(PemJwt.admin()))
            .andReturn();
    RevalidationFixtures.awaitNoRunningJob(jdbc);

    assertThat(r.getResponse().getHeader("X-Correlation-Id")).isEqualTo(uuid);
    JsonNode e = RevalidationFixtures.outbox(jdbc, "RevalidationDue").get(0);
    assertThat(UUID.fromString(e.path("correlationId").asText())).isNotNull();
  }

  // ---- the correlation id of a consumed event --------------------------------------------------

  private void completeACaseWith(String correlationId) {
    UUID eventId = UUID.randomUUID();
    RevalidationFixtures.insertCase(jdbc, NPI, LocalDate.of(2026, 2, 28), "IN_SCREENING");
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("screeningId", UUID.randomUUID().toString());
    data.put("npi", NPI);
    data.put("reason", "REVALIDATION");
    data.put("result", "PASS");
    EventProbe.send(
        PemContainers.KAFKA.getBootstrapServers(),
        RevalidationFixtures.SCREENING_TOPIC,
        NPI,
        RevalidationFixtures.envelope(
            eventId, "ScreeningCompleted", "screening-service", NPI, data, correlationId));
    Awaitility.await("event " + eventId + " processed by revalidation-service")
        .atMost(RevalidationFixtures.BOUND)
        .pollInterval(Duration.ofMillis(200))
        .until(
            () ->
                RevalidationFixtures.count(
                        jdbc, "SELECT count(*) FROM processed_event WHERE event_id = ?", eventId)
                    > 0);
  }

  private Logged completionLine() {
    List<Logged> lines =
        capture.lines.stream()
            .filter(l -> l.message().contains("COMPLETED") && l.message().contains(NPI))
            .toList();
    assertThat(lines).as("the log line of the completed case").hasSize(1);
    return lines.get(0);
  }

  @ParameterizedTest(
      name =
          "[MAM-48 AC-4] a ScreeningCompleted with {0} as correlation id: the log line of the case carries a safe id")
  @MethodSource("crafted")
  void consumerLogCarriesASafeId(String what, String raw) {
    completeACaseWith(raw);

    Logged line = completionLine();

    assertThat(line.mdc().get("correlationId")).matches(SAFE).isNotEqualTo(raw);
  }

  @ParameterizedTest(
      name =
          "[MAM-48 AC-4] a ScreeningCompleted with {0} as correlation id: no log line holds the raw value")
  @MethodSource("crafted")
  void consumerLogHoldsNoRawValue(String what, String raw) {
    completeACaseWith(raw);

    assertThat(capture.lines)
        .noneMatch(l -> l.message().contains("FORGED") || (!raw.isEmpty() && l.message().contains(raw)));
    assertThat(capture.lines)
        .noneMatch(l -> l.mdc().values().stream().anyMatch(v -> v.contains("FORGED")));
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-4] a ScreeningCompleted with a UUID as correlation id: the log line of the case carries that UUID")
  void consumerLogKeepsAUuid() {
    String uuid = UUID.randomUUID().toString();

    completeACaseWith(uuid);

    assertThat(completionLine().mdc()).containsEntry("correlationId", uuid);
  }
}
