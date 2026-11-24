package gov.medicaid.pem.apigateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import gov.medicaid.pem.kit.web.CorrelationId;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * MAM-29 AC-6 and D-024 (code review of 2026-11-21, MUST-FIX): the read limit of {@link
 * GatewayProxyTimeoutTest} was tested with GET calls only. The request factory is to be replaced,
 * and a write behaves differently from a read in a client (the body is sent first; some clients
 * repeat what they think is safe). So the same two properties are pinned for a POST, a PUT and a
 * DELETE to a service that accepts the call and never answers:
 *
 * <ul>
 *   <li>it ends in the usual 503 problem within the read limit (1 s here, see {@link
 *       UpstreamFailureTestBase});
 *   <li>the write reaches the service once: it is never repeated (a repeated PUT of the risk rules
 *       would create a second version).
 * </ul>
 *
 * They are guards, green with the current factory. Every test has its own 30 s guard in a separate
 * thread and every call its own 6 s limit, so a missing limit makes a test FAIL with a message and
 * never hang the build.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class GatewayWriteTimeoutTest extends UpstreamFailureTestBase {

  static final String RULES_BODY = "{\"rules\":[{\"taxonomyPrefix\":\"341\",\"tier\":\"M\"}]}";

  /** label, method, path, body: the stub holds every one of these paths without an answer. */
  static Stream<Arguments> silentWrites() {
    return Stream.of(
        arguments("POST /api/v1/risk-assessments", "POST", "/api/v1/risk-assessments", RULES_BODY),
        arguments("PUT /api/v1/risk-rules", "PUT", "/api/v1/risk-rules", RULES_BODY),
        arguments(
            "DELETE /api/v1/risk-rules/legacy-1", "DELETE", "/api/v1/risk-rules/legacy-1", null));
  }

  @ParameterizedTest(
      name =
          "[MAM-29 AC-6] a silent risk-service does not answer {0}: the gateway answers 503 problem+json")
  @MethodSource("silentWrites")
  @DisplayName("[MAM-29 AC-6] a write to a service that never answers is answered 503 problem+json")
  void silentServiceWriteIsAnswered503Problem(String label, String method, String path, String body)
      throws Exception {
    Answer answer = send(method, path, Map.of(CorrelationId.HEADER, "gw-write-timeout"), body);

    assertThat(answer.status()).isEqualTo(503);
    assertThat(answer.contentType()).contains("application/problem+json");
  }

  @ParameterizedTest(
      name =
          "[MAM-29 AC-6] a silent risk-service does not answer {0}: the gateway answers within the read limit")
  @MethodSource("silentWrites")
  @DisplayName(
      "[MAM-29 AC-6] a write to a service that never answers is ended by the read limit, not earlier and not much later")
  void silentServiceWriteIsEndedByTheReadLimit(
      String label, String method, String path, String body) throws Exception {
    Answer answer = send(method, path, Map.of(CorrelationId.HEADER, "gw-write-timeout"), body);

    assertThat(answer.elapsed()).isBetween(READ_LIMIT.minusMillis(200), Duration.ofSeconds(5));
  }

  @ParameterizedTest(
      name = "[MAM-29 AC-6] a silent risk-service does not answer {0}: the write reaches it once")
  @MethodSource("silentWrites")
  @DisplayName("[MAM-29 AC-6] a write to a service that never answers is not repeated")
  void silentServiceWriteIsNotRepeated(String label, String method, String path, String body)
      throws Exception {
    long before = STUB.callsTo(path);

    send(method, path, Map.of(CorrelationId.HEADER, "gw-write-timeout"), body);

    assertThat(STUB.callsTo(path) - before).isEqualTo(1);
  }
}
