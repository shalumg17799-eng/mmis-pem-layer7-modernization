package gov.medicaid.pem.apigateway;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import gov.medicaid.pem.kit.web.CorrelationId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * MAM-29 AC-6 and D-024 (code review of 2026-11-20, MUST-FIX 2): a forward that fails (a stopped
 * service, a service that does not answer in time) is answered 503, and the failure must also be
 * LOGGED, once, with the service name, the full exception (so the cause shows) and the correlation
 * id of the request. The route's error handler dropped the exception and logged nothing.
 *
 * <p>The log is read two ways: the console text (what an operator sees) and the log events (level,
 * message, cause chain and the MDC of the logging thread). Every test has its own 30 s guard and
 * every call its own limit, so a gateway that hangs makes a test FAIL and never hang the build.
 */
@ExtendWith(OutputCaptureExtension.class)
@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class GatewayFailureLoggingTest extends UpstreamFailureTestBase {

  /** The stopped service: its call is refused. */
  static final String STOPPED_PATH = "/api/v1/batch/tmsis-extracts";

  /** A service that accepts the call and never answers. */
  static final String SILENT_PATH = "/api/v1/risk-rules";

  /** Synthetic values that must never reach the log. */
  static final String TOKEN = "synthetic-token-7c1d9e";

  static final String BODY_MARKER = "BODY-MARKER-4f2a";

  /** The level word of a Boot console line: " WARN " or "ERROR". */
  private static final Pattern LEVEL = Pattern.compile("\\b(WARN|ERROR)\\b");

  /** One log event, copied on the logging thread (the MDC of an event is read lazily). */
  record Logged(Level level, String message, Map<String, String> mdc, List<String> causes) {}

  /** Collects every event of the JVM at the root logger, whichever class logs it. */
  static final class Collector extends AppenderBase<ILoggingEvent> {
    final List<Logged> events = new CopyOnWriteArrayList<>();

    @Override
    protected void append(ILoggingEvent event) {
      List<String> causes = new ArrayList<>();
      for (IThrowableProxy t = event.getThrowableProxy(); t != null; t = t.getCause()) {
        causes.add(t.getClassName());
      }
      Map<String, String> mdc = event.getMDCPropertyMap();
      events.add(
          new Logged(
              event.getLevel(),
              event.getFormattedMessage(),
              mdc == null ? new HashMap<>() : new HashMap<>(mdc),
              causes));
    }
  }

  private Logger root;
  private Collector collector;

  @BeforeEach
  void listen() {
    root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    collector = new Collector();
    collector.setContext(root.getLoggerContext());
    collector.start();
    root.addAppender(collector);
  }

  @AfterEach
  void stopListening() {
    root.detachAppender(collector);
    collector.stop();
  }

  /** The console lines that carry the level WARN or ERROR and name the service. */
  private static List<String> failureLines(String console, String service) {
    return console
        .lines()
        .filter(line -> LEVEL.matcher(line).find())
        .filter(line -> line.toLowerCase(Locale.ROOT).contains(service))
        .toList();
  }

  /** The events of level WARN or ERROR whose message names the service. */
  private List<Logged> failureEvents(String service) {
    return collector.events.stream()
        .filter(event -> event.level().isGreaterOrEqual(Level.WARN))
        .filter(event -> event.message().toLowerCase(Locale.ROOT).contains(service))
        .toList();
  }

  @Test
  @DisplayName(
      "[MAM-29 AC-6] a call to a stopped service is logged once at WARN or ERROR, naming the service")
  void stoppedServiceIsLoggedOnce(CapturedOutput output) throws Exception {
    send("POST", STOPPED_PATH, Map.of(CorrelationId.HEADER, "gw-503"), "{}");

    assertThat(failureLines(output.getAll(), "tmsis")).hasSize(1);
  }

  @Test
  @DisplayName(
      "[MAM-29 AC-6] the log of a call to a stopped service shows the cause (the stack trace names ConnectException)")
  void stoppedServiceLogShowsTheCause(CapturedOutput output) throws Exception {
    send("POST", STOPPED_PATH, Map.of(CorrelationId.HEADER, "gw-503"), "{}");

    assertThat(output.getAll()).contains("ConnectException");
  }

  @Test
  @DisplayName(
      "[MAM-29 AC-6] the log of a failed call holds neither the bearer token nor the request body")
  void logHoldsNoTokenAndNoBody(CapturedOutput output) throws Exception {
    send(
        "POST",
        STOPPED_PATH,
        Map.of(CorrelationId.HEADER, "gw-503", "Authorization", "Bearer " + TOKEN),
        "{\"note\":\"" + BODY_MARKER + "\"}");

    assertThat(failureLines(output.getAll(), "tmsis"))
        .as("the failure is logged at all (else this test proves nothing)")
        .isNotEmpty();
    assertThat(output.getAll()).doesNotContain(TOKEN).doesNotContain(BODY_MARKER);
  }

  @Test
  @DisplayName(
      "[MAM-29 AC-6] the log line of a failed call carries the correlation id of the request (MDC)")
  void logLineCarriesTheCorrelationId() throws Exception {
    send("POST", STOPPED_PATH, Map.of(CorrelationId.HEADER, "gw-503"), "{}");

    assertThat(failureEvents("tmsis"))
        .singleElement()
        .satisfies(
            logged -> assertThat(logged.mdc()).containsEntry(CorrelationId.MDC_KEY, "gw-503"));
  }

  @Test
  @DisplayName(
      "[MAM-29 AC-6] the log event of a failed call holds the exception itself (ConnectException in its cause chain)")
  void logEventHoldsTheException() throws Exception {
    send("POST", STOPPED_PATH, Map.of(CorrelationId.HEADER, "gw-503"), "{}");

    assertThat(failureEvents("tmsis"))
        .singleElement()
        .satisfies(
            logged ->
                assertThat(logged.causes()).anyMatch(name -> name.endsWith("ConnectException")));
  }

  @Test
  @DisplayName(
      "[MAM-29 AC-6] a call to a service that does not answer in time is logged once, naming the service and a timeout as the cause")
  void timedOutCallIsLogged() throws Exception {
    get(SILENT_PATH, Map.of(CorrelationId.HEADER, "gw-timeout-log"));

    assertThat(failureEvents("risk"))
        .singleElement()
        .satisfies(
            logged -> assertThat(logged.causes()).anyMatch(name -> name.contains("Timeout")));
  }

  /**
   * A guard, green before the fix: logging the failure must not change what the caller gets: the
   * same 503 problem, with no host, port or cause in it.
   */
  @Test
  @DisplayName(
      "[MAM-29 AC-6] the answer for a stopped service stays the plain 503 problem, with no host, port or cause")
  void answerStaysThePlainProblem() throws Exception {
    Answer answer = send("POST", STOPPED_PATH, Map.of(CorrelationId.HEADER, "gw-503"), "{}");

    assertThat(answer.status()).isEqualTo(503);
    assertThat(answer.contentType()).contains("application/problem+json");
    assertThat(answer.body())
        .contains("\"status\":503")
        .contains("gw-503")
        .doesNotContain("127.0.0.1")
        .doesNotContain(String.valueOf(STOPPED_PORT))
        .doesNotContain("Exception")
        .doesNotContain("Connect")
        .doesNotContain("\tat ");
  }
}
