package gov.medicaid.pem.apigateway;

import static gov.medicaid.pem.apigateway.AnsweringServiceStub.STATUS_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * MAM-29 AC-2, AC-6, D-023 and D-024 (code review of 2026-11-21, MUST-FIX): a service's error
 * answer must reach the caller whole, whatever the method. The gateway's request factory (the
 * {@code simple} one of the review fix of 2026-11-21) streams the body of a POST, PUT and DELETE;
 * {@code HttpURLConnection} then refuses to handle a 401 (the review: it throws {@code
 * HttpRetryException}). What the caller gets, seen in the first red run: the status line and the
 * headers of the 401 (with the announced {@code Content-Length}), and then no body, and the
 * connection stays open, so a client waits for the missing bytes. A caller whose token expired is
 * told so by exactly that answer.
 *
 * <p>The stub answers as a protected service does (see {@link AnsweringServiceStub}): 401 with
 * {@code WWW-Authenticate: Bearer} and a problem+json body. The tests say what must reach the
 * caller, not how the gateway gets it, so they hold for any request factory.
 *
 * <p>The first group is the review item (one test per property, so a failure names what is lost);
 * the GET case in it is a control that works today. The second group is a guard: every other error
 * status of a service is relayed with its own body for every method, and must stay so whatever
 * client replaces the current one (a client that retries or rewrites an answer would show here).
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class GatewayRelaysServiceErrorTest extends ServiceAnswerTestBase {

  /**
   * Calls of every method the gateway forwards, with paths of the contracts (no contract has a
   * DELETE yet, but the route table forwards every method of a path). The last one is the control.
   */
  static Stream<Arguments> protectedCalls() {
    return Stream.of(
        arguments(
            "POST /api/v1/applications with a JSON body",
            "POST",
            "/api/v1/applications",
            APPLICATION_BODY),
        arguments(
            "PUT /api/v1/risk-rules with a JSON body", "PUT", "/api/v1/risk-rules", RULES_BODY),
        arguments(
            "DELETE /api/v1/providers/1003000111", "DELETE", "/api/v1/providers/1003000111", null),
        arguments(
            "POST /api/v1/batch/revalidation-sweeps without a body",
            "POST",
            "/api/v1/batch/revalidation-sweeps",
            null),
        arguments("GET /api/v1/providers (control)", "GET", "/api/v1/providers", null));
  }

  /** The methods and paths of the guard group. */
  static final List<String[]> METHOD_PATHS =
      List.of(
          new String[] {"GET", "/api/v1/providers"},
          new String[] {"POST", "/api/v1/applications"},
          new String[] {"PUT", "/api/v1/risk-rules"},
          new String[] {"DELETE", "/api/v1/providers/1003000111"});

  static final List<Integer> ERROR_STATUSES = List.of(400, 403, 404, 409, 422, 500, 503);

  static Stream<Arguments> everyErrorStatusOfEveryMethod() {
    return METHOD_PATHS.stream()
        .flatMap(
            mp ->
                ERROR_STATUSES.stream()
                    .map(
                        status ->
                            arguments(
                                mp[0],
                                mp[1],
                                status,
                                mp[0].equals("POST") || mp[0].equals("PUT") ? RULES_BODY : null)));
  }

  /** A call with no token (the usual reason for a 401) to a service that answers 401. */
  private Req unauthorizedCall(String method, String path, String body) {
    Req req = call(method, path).header(STATUS_HEADER, "401");
    return body == null ? req : req.body(body, JSON);
  }

  @ParameterizedTest(name = "[MAM-29 AC-2/AC-6] {0}: the service's 401 reaches the caller as 401")
  @MethodSource("protectedCalls")
  @DisplayName(
      "[MAM-29 AC-2/AC-6] a service's 401 answer reaches the caller as 401, whatever the method")
  void unauthorizedStatusIsRelayed(String label, String method, String path, String body)
      throws Exception {
    Answer answer = unauthorizedCall(method, path, body).send();

    assertThat(answer.status()).isEqualTo(401);
  }

  @ParameterizedTest(
      name = "[MAM-29 AC-2/AC-6] {0}: the service's 401 reaches the caller as problem+json")
  @MethodSource("protectedCalls")
  @DisplayName(
      "[MAM-29 AC-2/AC-6] a service's 401 answer reaches the caller as problem+json, whatever the method")
  void unauthorizedIsProblemJson(String label, String method, String path, String body)
      throws Exception {
    Answer answer = unauthorizedCall(method, path, body).send();

    assertThat(answer.contentType()).contains("application/problem+json");
  }

  @ParameterizedTest(
      name = "[MAM-29 AC-2/AC-6] {0}: the problem body of the service's 401 arrives intact")
  @MethodSource("protectedCalls")
  @DisplayName(
      "[MAM-29 AC-2/AC-6] the problem body of a service's 401 arrives intact, whatever the method")
  void unauthorizedBodyIsIntact(String label, String method, String path, String body)
      throws Exception {
    Answer answer = unauthorizedCall(method, path, body).send();

    assertThat(answer.text()).isEqualTo(AnsweringServiceStub.problemBody(401, path));
  }

  @ParameterizedTest(
      name = "[MAM-29 AC-2/AC-6] {0}: the Bearer challenge of the service's 401 arrives")
  @MethodSource("protectedCalls")
  @DisplayName(
      "[MAM-29 AC-2/AC-6] the Bearer challenge of a service's 401 arrives, whatever the method")
  void unauthorizedKeepsTheBearerChallenge(String label, String method, String path, String body)
      throws Exception {
    Answer answer = unauthorizedCall(method, path, body).send();

    assertThat(answer.header("WWW-Authenticate")).isEqualTo("Bearer");
  }

  @ParameterizedTest(
      name =
          "[MAM-29 AC-2/AC-6] {0}: the answer to a 401 carries the correlation id of the request")
  @MethodSource("protectedCalls")
  @DisplayName(
      "[MAM-29 AC-2/AC-6] the answer to a 401 carries the correlation id of the request, whatever the method")
  void unauthorizedCarriesTheCorrelationId(String label, String method, String path, String body)
      throws Exception {
    Req req = unauthorizedCall(method, path, body);

    Answer answer = req.send();

    assertThat(answer.header("X-Correlation-Id")).isEqualTo(req.correlationId());
  }

  @ParameterizedTest(
      name = "[MAM-29 AC-2/AC-6] {0}: a call that is answered 401 reaches the service once")
  @MethodSource("protectedCalls")
  @DisplayName(
      "[MAM-29 AC-2/AC-6] a call that is answered 401 reaches the service once, whatever the method")
  void unauthorizedCallIsNotRepeated(String label, String method, String path, String body)
      throws Exception {
    Req req = unauthorizedCall(method, path, body);

    try {
      req.send();
    } catch (AssertionError brokenAnswer) {
      // the answer is judged by the other tests of this class; here only how often the service
      // was asked counts, so a lost answer must not hide a repeated call
    }

    assertThat(req.serviceCalls()).hasSize(1);
  }

  @ParameterizedTest(
      name = "[MAM-29 AC-6] {0} {1}: the service's {2} reaches the caller with the same status")
  @MethodSource("everyErrorStatusOfEveryMethod")
  @DisplayName(
      "[MAM-29 AC-6] the status of a service's error answer reaches the caller, for every method")
  void errorStatusIsRelayed(String method, String path, int status, String body) throws Exception {
    Req req = call(method, path).header(STATUS_HEADER, String.valueOf(status));

    Answer answer = (body == null ? req : req.body(body, JSON)).send();

    assertThat(answer.status()).isEqualTo(status);
  }

  @ParameterizedTest(
      name =
          "[MAM-29 AC-6] {0} {1}: the problem body of the service's {2} arrives as the service wrote it")
  @MethodSource("everyErrorStatusOfEveryMethod")
  @DisplayName(
      "[MAM-29 AC-6] the problem body of a service's error answer arrives as the service wrote it, for every method")
  void errorBodyIsRelayed(String method, String path, int status, String body) throws Exception {
    Req req = call(method, path).header(STATUS_HEADER, String.valueOf(status));

    Answer answer = (body == null ? req : req.body(body, JSON)).send();

    assertThat(answer.text()).isEqualTo(AnsweringServiceStub.problemBody(status, path));
  }
}
