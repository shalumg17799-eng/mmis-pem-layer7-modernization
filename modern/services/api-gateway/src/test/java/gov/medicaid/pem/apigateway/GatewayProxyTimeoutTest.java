package gov.medicaid.pem.apigateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import gov.medicaid.pem.kit.web.CorrelationId;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * MAM-29 AC-6 and D-024 (code review of 2026-11-20, MUST-FIX 1): a service that accepts the call
 * and never answers must end in the gateway's 503 problem answer within the read limit. The proxy
 * had no connect or read timeout, so the caller waited for ever. Here the gateway's read limit is 1
 * s (see {@link UpstreamFailureTestBase}) and the stub never answers.
 *
 * <p>Every test has its own 30 s guard in a separate thread and every call its own 6 s limit, so a
 * missing limit makes a test FAIL with a message and never hang the build.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class GatewayProxyTimeoutTest extends UpstreamFailureTestBase {

  static final String RISK_PATH = "/api/v1/risk-rules";
  static final String CORRELATION = "gw-timeout-1";

  /**
   * The first GET path of each service's contract. {@code tmsis} is the stopped service of this
   * context (its refused call is in {@link GatewayFailureLoggingTest}).
   */
  static Stream<Arguments> silentServices() {
    return Stream.of(
        arguments("provider", "/api/v1/providers"),
        arguments("enrollment", "/api/v1/applications"),
        arguments("screening", "/api/v1/screenings"),
        arguments("risk", RISK_PATH),
        arguments("revalidation", "/api/v1/revalidations"),
        arguments("sanctions", "/api/v1/sanctions"),
        arguments("notification", "/api/v1/notices"),
        arguments("directory", "/api/v1/directory/providers"));
  }

  @ParameterizedTest(name = "[MAM-29 AC-6] a silent {0}-service ({1}) is answered 503 problem+json")
  @MethodSource("silentServices")
  @DisplayName(
      "[MAM-29 AC-6] a service that accepts the call and never answers is answered 503 problem+json")
  void silentServiceIsAnswered503Problem(String service, String path) throws Exception {
    Answer answer = get(path, Map.of(CorrelationId.HEADER, "gw-timeout-" + service));

    assertThat(answer.status()).isEqualTo(503);
    assertThat(answer.contentType()).contains("application/problem+json");
  }

  @Test
  @DisplayName(
      "[MAM-29 AC-6] the answer for a silent service carries the correlation id of the request")
  void answerCarriesTheCorrelationId() throws Exception {
    Answer answer = get(RISK_PATH, Map.of(CorrelationId.HEADER, CORRELATION));

    assertThat(answer.json().findValue("correlationId").asText()).isEqualTo(CORRELATION);
    assertThat(answer.correlationId()).isEqualTo(CORRELATION);
  }

  @Test
  @DisplayName(
      "[MAM-29 AC-6] the answer for a silent service names no host, port, exception or timeout text")
  void answerHasNoInternalDetail() throws Exception {
    Answer answer = get(RISK_PATH, Map.of(CorrelationId.HEADER, CORRELATION));

    assertThat(answer.body())
        .contains("\"status\":503")
        .doesNotContain("127.0.0.1")
        .doesNotContain(String.valueOf(STUB.port()))
        .doesNotContain("Exception")
        .doesNotContain("timed out");
  }

  @Test
  @DisplayName(
      "[MAM-29 AC-6] the answer for a silent service is the same 503 problem as for a stopped one (status, title, detail)")
  void answerIsTheUsualUnavailableProblem() throws Exception {
    Answer answer = get(RISK_PATH, Map.of(CorrelationId.HEADER, CORRELATION));

    assertThat(answer.json().path("status").asInt()).isEqualTo(503);
    assertThat(answer.json().path("title").asText()).isEqualTo("Service Unavailable");
    assertThat(answer.json().path("detail").asText())
        .isEqualTo("The risk service is not available. Try again later.");
  }

  @Test
  @DisplayName("[MAM-29 AC-6] a call to a silent service ends within a bounded time")
  void endsWithinABoundedTime() throws Exception {
    Answer answer = get(RISK_PATH, Map.of(CorrelationId.HEADER, CORRELATION));

    assertThat(answer.elapsed()).isLessThan(Duration.ofSeconds(5));
  }

  @Test
  @DisplayName(
      "[MAM-29 AC-6] the call reached the silent service and was ended by the read limit, not by an earlier error")
  void isEndedByTheReadLimit() throws Exception {
    long before = STUB.callsTo(RISK_PATH);

    Answer answer = get(RISK_PATH, Map.of(CorrelationId.HEADER, CORRELATION));

    assertThat(STUB.callsTo(RISK_PATH)).isGreaterThan(before);
    assertThat(answer.elapsed()).isGreaterThanOrEqualTo(READ_LIMIT.minusMillis(200));
  }

  /**
   * A guard, green before the fix: the limit must not cut off a download that keeps flowing. The
   * stub sends the headers and the first part at once and four more parts, 0.5 s apart: about 2 s
   * in all against a read limit of 1 s, but never a pause as long as the limit (the code review
   * said: "the read limit is the time until response headers arrive, so streamed downloads are not
   * cut off"; the whole master export and the batch files are the slowest calls behind the
   * gateway).
   */
  @Test
  @DisplayName(
      "[MAM-29 AC-6] a download that keeps flowing is not cut off, although it takes longer than the read limit")
  void flowingDownloadIsNotCutOff() throws Exception {
    Answer answer = get(SlowServiceStub.DOWNLOAD_PATH, Map.of());

    assertThat(answer.status()).isEqualTo(200);
    assertThat(answer.body()).isEqualTo(SlowServiceStub.DOWNLOAD_BODY);
    assertThat(answer.elapsed())
        .as("the transfer must outlast the read limit, or this test proves nothing")
        .isGreaterThanOrEqualTo(
            Duration.ofMillis(
                SlowServiceStub.GAP_MILLIS * (SlowServiceStub.DOWNLOAD_PARTS - 1) - 200));
  }

  /**
   * The other half of "every outgoing call has a timeout": a service that sends its first bytes and
   * then stops must not hold the caller either. How the call ends (an error answer, or a cut
   * connection after bytes were already sent) is not decided here; that it ends in a bounded time
   * is.
   */
  @Test
  @DisplayName(
      "[MAM-29 AC-6] a download that stalls after its first bytes is ended within a bounded time")
  void stalledDownloadIsEndedWithinABoundedTime() throws Exception {
    long started = System.nanoTime();
    try {
      get(SlowServiceStub.STALLED_PATH, Map.of());
    } catch (IOException cut) {
      // the gateway cut the connection after it had sent the first bytes: the call ended
    }

    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
  }
}
