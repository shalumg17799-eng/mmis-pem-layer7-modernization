package gov.medicaid.pem.apigateway;

import static gov.medicaid.pem.apigateway.AnsweringServiceStub.CHUNKED_HEADER;
import static gov.medicaid.pem.apigateway.AnsweringServiceStub.DOWNLOAD_HEADER;
import static gov.medicaid.pem.apigateway.AnsweringServiceStub.DROP_HEADER;
import static gov.medicaid.pem.apigateway.AnsweringServiceStub.STATUS_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * MAM-29 AC-1, AC-3, AC-5, AC-6, D-024 (code review of 2026-11-21, MUST-FIX): the request factory
 * of the gateway is to be replaced by one without the {@code HttpURLConnection} rule that loses a
 * service's 401 to a write ({@link GatewayRelaysServiceErrorTest}). These tests guard everything
 * else a replacement must keep. They go through the shipped {@code api-gateway.yml} and a stub that
 * records what it receives, and say nothing about which client is used.
 *
 * <p>Guards, green with the current factory, so a replacement that breaks one is caught at once:
 *
 * <ul>
 *   <li>what a service receives from a POST, PUT and DELETE: body, content type, the bearer token
 *       (AC-3) and the correlation id (AC-5), a body of about 1 MB (the legacy file uploads of the
 *       enrollment and sanctions contracts are multipart requests);
 *   <li>what the caller receives: a download of 2 MB, with or without a content length, the {@code
 *       Location} of a 202 and the {@code Content-Disposition} of a file (the web UI reads both,
 *       AC-4 exposes them);
 *   <li>a call is never repeated: a write is not sent twice when the service closes the connection
 *       without an answer (a repeated PUT of the risk rules would create a second version), and a
 *       service's own 503 or 429 is relayed once and not retried (Apache's default retry strategy
 *       retries both).
 * </ul>
 *
 * <p>Two tests are red with the current factory, for a reason of the same family: PATCH. The route
 * table forwards every method and the gateway's CORS answer allows PATCH, but {@code
 * HttpURLConnection} knows no PATCH (the developer's note of 2026-11-21; the first red run shows
 * {@code ProtocolException: Invalid HTTP method: PATCH}): the call ends in the 503 problem. No
 * contract has a PATCH today; both replacements the review names (Apache HttpClient 5, the JDK
 * HttpClient) support it. These two tests are not part of the review's item: they are a regression
 * guard for a method the gateway promised; keeping them is the lead's decision (red report).
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class GatewayRelaysWritesTest extends ServiceAnswerTestBase {

  static final String BOUNDARY = "pem-test-boundary-7d3f";
  static final String MULTIPART = "multipart/form-data; boundary=" + BOUNDARY;
  static final String PATCH_PATH = "/api/v1/providers/1003000111";
  static final String UPLOAD_PATH = "/api/v1/batch/sanction-intake-runs";
  static final int DOWNLOAD_SIZE = 2_000_000;

  /** label, method, path, content type, body. */
  static Stream<Arguments> writesWithABody() {
    return Stream.of(
        arguments(
            "POST /api/v1/applications", "POST", "/api/v1/applications", JSON, APPLICATION_BODY),
        arguments("PUT /api/v1/risk-rules", "PUT", "/api/v1/risk-rules", JSON, RULES_BODY),
        arguments(
            "POST /api/v1/batch/sanction-intake-runs (multipart)",
            "POST",
            UPLOAD_PATH,
            MULTIPART,
            multipartUpload(200)));
  }

  /** label, method, path. */
  static Stream<Arguments> everyWrite() {
    return Stream.of(
        arguments("POST /api/v1/applications", "POST", "/api/v1/applications"),
        arguments("PUT /api/v1/risk-rules", "PUT", "/api/v1/risk-rules"),
        arguments("DELETE /api/v1/providers/1003000111", "DELETE", "/api/v1/providers/1003000111"));
  }

  /** method, status: the answers a retrying client would repeat. */
  static Stream<Arguments> retriableAnswers() {
    return Stream.of("GET", "POST", "PUT", "DELETE")
        .flatMap(method -> Stream.of(429, 503).map(status -> arguments(method, status)));
  }

  /**
   * A multipart upload as the batch intake endpoints take it: a business date and a file of 80-byte
   * records (the layout of SANCIN), as many records as given, no line breaks.
   */
  static byte[] multipartUpload(int records) {
    StringBuilder text = new StringBuilder();
    text.append("--").append(BOUNDARY).append("\r\n");
    text.append("Content-Disposition: form-data; name=\"businessDate\"\r\n\r\n2026-01-15\r\n");
    text.append("--").append(BOUNDARY).append("\r\n");
    text.append(
        "Content-Disposition: form-data; name=\"file\"; filename=\"SANCIN.dat\"\r\n"
            + "Content-Type: application/octet-stream\r\n\r\n");
    for (int i = 0; i < records; i++) {
      String line = String.format("%010d", 1003000000L + i) + "T" + "SYNTHETIC REASON " + i;
      text.append(String.format("%-80s", line), 0, 80);
    }
    text.append("\r\n--").append(BOUNDARY).append("--\r\n");
    return text.toString().getBytes(StandardCharsets.UTF_8);
  }

  static String sha256(byte[] data) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
  }

  @ParameterizedTest(name = "[MAM-29 AC-1] {0}: the service receives the body unchanged")
  @MethodSource("writesWithABody")
  @DisplayName("[MAM-29 AC-1] a POST or PUT body reaches the service byte for byte")
  void bodyReachesTheServiceUnchanged(
      String label, String method, String path, String type, Object body) throws Exception {
    Req req = call(method, path).body(asBytes(body), type);

    req.send();

    assertThat(req.serviceCalls())
        .singleElement()
        .satisfies(seen -> assertThat(seen.body()).isEqualTo(asBytes(body)));
  }

  /**
   * The media type (and the boundary of a multipart body) arrives as sent. The gateway's servlet
   * layer adds {@code ;charset=UTF-8} to the header whatever client forwards the call (Spring's
   * {@code ServletServerHttpRequest} copies the request encoding into the content type: seen in the
   * first red run, with the current client), and writes the multipart parameters without a space:
   * the same media type, so the parameter {@code charset} and the spacing are not compared.
   */
  @ParameterizedTest(name = "[MAM-29 AC-1] {0}: the service receives the same media type")
  @MethodSource("writesWithABody")
  @DisplayName(
      "[MAM-29 AC-1] the media type of a POST or PUT, with the boundary of a multipart body, reaches the service")
  void contentTypeReachesTheService(
      String label, String method, String path, String type, Object body) throws Exception {
    Req req = call(method, path).body(asBytes(body), type);

    req.send();

    MediaType sent = MediaType.parseMediaType(type);
    assertThat(req.serviceCalls())
        .singleElement()
        .satisfies(
            seen -> {
              MediaType got = MediaType.parseMediaType(seen.header("Content-Type"));
              assertThat(got.getType() + "/" + got.getSubtype())
                  .isEqualTo(sent.getType() + "/" + sent.getSubtype());
              assertThat(got.getParameter("boundary")).isEqualTo(sent.getParameter("boundary"));
            });
  }

  @ParameterizedTest(name = "[MAM-29 AC-3] {0}: the service receives the bearer token unchanged")
  @MethodSource("everyWrite")
  @DisplayName(
      "[MAM-29 AC-3] the bearer token of a POST, PUT or DELETE reaches the service unchanged")
  void tokenReachesTheServiceOnWrites(String label, String method, String path) throws Exception {
    Req req = call(method, path).header("Authorization", "Bearer synthetic-token-" + method);

    req.send();

    assertThat(req.serviceCalls())
        .singleElement()
        .satisfies(
            seen ->
                assertThat(seen.header("Authorization"))
                    .isEqualTo("Bearer synthetic-token-" + method));
  }

  @ParameterizedTest(name = "[MAM-29 AC-5] {0}: the service receives the correlation id")
  @MethodSource("everyWrite")
  @DisplayName("[MAM-29 AC-5] the correlation id of a POST, PUT or DELETE reaches the service")
  void correlationIdReachesTheServiceOnWrites(String label, String method, String path)
      throws Exception {
    Req req = call(method, path);

    req.send();

    assertThat(req.serviceCalls())
        .singleElement()
        .satisfies(
            seen -> assertThat(seen.header("X-Correlation-Id")).isEqualTo(req.correlationId()));
  }

  @Test
  @DisplayName(
      "[MAM-29 AC-1] a POST without a body (a run start) reaches the service with an empty body")
  void postWithoutBodyReachesTheServiceEmpty() throws Exception {
    Req req = call("POST", "/api/v1/batch/revalidation-sweeps");

    req.send();

    assertThat(req.serviceCalls())
        .singleElement()
        .satisfies(
            seen -> {
              assertThat(seen.method()).isEqualTo("POST");
              assertThat(seen.body()).isEmpty();
            });
  }

  @Test
  @DisplayName("[MAM-29 AC-1] an upload of about 1 MB reaches the service whole")
  void largeUploadReachesTheServiceWhole() throws Exception {
    byte[] upload = multipartUpload(13_000);
    Req req = call("POST", UPLOAD_PATH).body(upload, MULTIPART);

    req.send();

    assertThat(req.serviceCalls()).as("calls that reached the service").hasSize(1);
    byte[] received = req.serviceCalls().get(0).body();
    assertThat(sha256(received))
        .as(
            "sha-256 of the %d bytes the service received (sent: %d)",
            received.length, upload.length)
        .isEqualTo(sha256(upload));
  }

  @ParameterizedTest(name = "[MAM-29 AC-1] a download of 2 MB arrives whole (chunked: {0})")
  @ValueSource(booleans = {false, true})
  @DisplayName("[MAM-29 AC-1] a download of 2 MB arrives whole, with and without a content length")
  void largeDownloadArrivesWhole(boolean chunked) throws Exception {
    Answer answer =
        call("GET", "/api/v1/legacy/provider-master")
            .header(DOWNLOAD_HEADER, String.valueOf(DOWNLOAD_SIZE))
            .header(CHUNKED_HEADER, String.valueOf(chunked))
            .send();

    assertThat(sha256(answer.body()))
        .as(
            "sha-256 of the %d bytes the caller received (sent: %d)",
            answer.body().length, DOWNLOAD_SIZE)
        .isEqualTo(sha256(AnsweringServiceStub.download(DOWNLOAD_SIZE)));
  }

  @Test
  @DisplayName("[MAM-29 AC-1] the Content-Disposition of a file download reaches the caller")
  void downloadKeepsItsFileName() throws Exception {
    Answer answer =
        call("GET", "/api/v1/legacy/provider-master").header(DOWNLOAD_HEADER, "1000").send();

    assertThat(answer.header("Content-Disposition")).isEqualTo(AnsweringServiceStub.DISPOSITION);
  }

  @Test
  @DisplayName(
      "[MAM-29 AC-1] the Location of a 202 answer reaches the caller as the service wrote it")
  void acceptedAnswerKeepsItsLocation() throws Exception {
    Answer answer =
        call("POST", "/api/v1/batch/revalidation-sweeps").header(STATUS_HEADER, "202").send();

    assertThat(answer.header("Location")).isEqualTo(AnsweringServiceStub.LOCATION);
  }

  @Test
  @DisplayName(
      "[MAM-29 AC-1] a PATCH reaches the service with its body (the route table forwards every method)")
  void patchReachesTheService() throws Exception {
    Req req = call("PATCH", PATCH_PATH).body("{\"note\":\"patched\"}", JSON);

    req.send();

    assertThat(req.serviceCalls())
        .singleElement()
        .satisfies(
            seen -> {
              assertThat(seen.method()).isEqualTo("PATCH");
              assertThat(seen.bodyText()).isEqualTo("{\"note\":\"patched\"}");
            });
  }

  @Test
  @DisplayName("[MAM-29 AC-1] the service's answer to a PATCH reaches the caller")
  void patchAnswerReachesTheCaller() throws Exception {
    Answer answer = call("PATCH", PATCH_PATH).body("{\"note\":\"patched\"}", JSON).send();

    assertThat(answer.status()).isEqualTo(200);
  }

  @ParameterizedTest(
      name =
          "[MAM-29 AC-6] {0}: a service that closes the connection without an answer is answered 503 problem+json")
  @MethodSource("everyWrite")
  @DisplayName(
      "[MAM-29 AC-6] a write to a service that closes the connection without an answer is answered 503 problem+json")
  void closedConnectionIsAnswered503(String label, String method, String path) throws Exception {
    Answer answer = withBody(call(method, path).header(DROP_HEADER, "yes"), method).send();

    assertThat(answer.status()).isEqualTo(503);
    assertThat(answer.contentType()).contains("application/problem+json");
  }

  @ParameterizedTest(
      name = "[MAM-29 AC-6] {0}: a write is not sent twice when the service closes the connection")
  @MethodSource("everyWrite")
  @DisplayName(
      "[MAM-29 AC-6] a write is never repeated when the service closes the connection without an answer")
  void writeIsNotRepeatedAfterAClosedConnection(String label, String method, String path)
      throws Exception {
    Req req = withBody(call(method, path).header(DROP_HEADER, "yes"), method);

    req.send();

    assertThat(req.serviceCalls()).hasSize(1);
  }

  @ParameterizedTest(name = "[MAM-29 AC-6] {0}: the service's {1} is relayed and not retried")
  @MethodSource("retriableAnswers")
  @DisplayName(
      "[MAM-29 AC-6] a service's own 503 or 429 is relayed once, not retried by the gateway")
  void serviceAnswerIsNotRetried(String method, int status) throws Exception {
    Req req =
        call(method, "/api/v1/providers/1003000111").header(STATUS_HEADER, String.valueOf(status));

    req.send();

    assertThat(req.serviceCalls()).hasSize(1);
  }

  /** A POST and a PUT carry a body, as the real writes do; a DELETE does not. */
  private static Req withBody(Req req, String method) {
    return method.equals("DELETE") ? req : req.body(RULES_BODY, JSON);
  }

  private static byte[] asBytes(Object body) {
    return body instanceof byte[] bytes ? bytes : ((String) body).getBytes(StandardCharsets.UTF_8);
  }
}
