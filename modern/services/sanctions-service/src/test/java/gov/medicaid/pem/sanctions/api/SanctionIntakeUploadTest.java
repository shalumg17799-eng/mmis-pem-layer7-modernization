package gov.medicaid.pem.sanctions.api;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.sanctions.SancinFile;
import gov.medicaid.pem.sanctions.TestProviderService;
import gov.medicaid.pem.testsupport.PemContainers;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * MAM-55 AC-1 against the real web server (code review 2026-11-20, Q-143, lesson h of the reviews:
 * a body that cannot be read is a 400 problem, never a 500). The SANCIN file arrives as a multipart
 * upload; MockMvc does not parse multipart messages the way Tomcat does, so this class starts the
 * service on a random port (the tokens are accepted by a test decoder) and sends the bytes over
 * HTTP.
 *
 * <p>Two malformed bodies are answered 500 with Spring Boot's default JSON body today: a content
 * type {@code multipart/form-data} without a boundary parameter, and a file part that is cut off
 * (no closing boundary). Both, and two more of the same kind, must be answered like enrollment-
 * service answers them (IntakeUploadLimitTest, MAM-56 AC-7): 400 {@code application/problem+json}
 * whose detail says that the upload could not be read, with the correlation id of the call, no SQL
 * or class name in the body, no run started and nothing applied.
 *
 * <p>Guards that pass today and must stay green: a file under the web server's limit is accepted, a
 * file over it stays refused with 413 (the mapping of the multipart errors must not swallow
 * Spring's MaxUploadSizeExceededException, Q-120), and a body with no part at all or without one of
 * the two required parts is the 400 problem of AC-1.
 *
 * <p>The class has a Spring context of its own (real web server), closed after the class: the test
 * JVM has a small heap. The context does NOT register the shared Postgres and Kafka containers as
 * beans (no @ImportTestcontainers): a Spring context stops the container beans it owns when it
 * closes, which would stop the containers the other test classes still use. They are wired through
 * properties. Every call has a time limit of 30 seconds, every wait is bounded and every test has a
 * limit of its own, so a defect makes a test fail and never hangs the build.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.config.name=sanctions-service", "PEM_LOG_FORMAT="})
@Import(SanctionIntakeUploadTest.AdminTokens.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class SanctionIntakeUploadTest {

  static final String BOUNDARY = "----pem-sanctions-test-boundary";
  static final String MULTIPART = "multipart/form-data; boundary=" + BOUNDARY;
  static final String NPI = "1003000111";
  static final String RECORD = SancinFile.record(NPI, "T", "SYNTHETIC TEST REASON", "20260331");
  static final String SAFE_ID = "[A-Za-z0-9._:-]{1,64}";

  /** The size of a file over every limit of the web server (1 MB Spring default, 2 MB Tomcat). */
  static final int OVER_THE_LIMIT = 2 * 1024 * 1024 + 100_000;

  /** The size of a file under every limit of the web server. */
  static final int UNDER_THE_LIMIT = 900_000;

  static final String BUSINESS_DATE_PART =
      "--"
          + BOUNDARY
          + "\r\nContent-Disposition: form-data; name=\"businessDate\"\r\n\r\n2026-03-31\r\n";
  static final String FILE_PART_HEAD =
      "--"
          + BOUNDARY
          + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"sancin.dat\"\r\n"
          + "Content-Type: application/octet-stream\r\n\r\n";
  static final String CLOSING = "\r\n--" + BOUNDARY + "--\r\n";

  /**
   * The shared containers of the other test classes, started here too when this class runs first.
   */
  @DynamicPropertySource
  static void sources(DynamicPropertyRegistry registry) {
    PemContainers.POSTGRES.start();
    PemContainers.KAFKA.start();
    registry.add("spring.datasource.url", PemContainers.POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", PemContainers.POSTGRES::getUsername);
    registry.add("spring.datasource.password", PemContainers.POSTGRES::getPassword);
    registry.add("spring.kafka.bootstrap-servers", PemContainers.KAFKA::getBootstrapServers);
    TestProviderService.register(registry);
  }

  /**
   * Any bearer token is an ADMIN login: the real web server needs a token to reach the endpoint.
   */
  @TestConfiguration
  static class AdminTokens {
    @Bean
    @Primary
    JwtDecoder adminDecoder() {
      return token ->
          Jwt.withTokenValue(token)
              .header("alg", "none")
              .subject("sub-admin1")
              .claim("preferred_username", "admin1")
              .claim("realm_access", Map.of("roles", List.of("ADMIN")))
              .issuedAt(Instant.now())
              .expiresAt(Instant.now().plusSeconds(3600))
              .build();
    }
  }

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  final HttpClient client =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .connectTimeout(Duration.ofSeconds(10))
          .build();

  void awaitNoJob() {
    Awaitility.await("no sanction intake job is still running")
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () ->
                jdbc.queryForObject(
                        "SELECT count(*) FROM batch_job_execution"
                            + " WHERE status IN ('STARTING', 'STARTED')",
                        Integer.class)
                    == 0);
  }

  @BeforeEach
  @AfterEach
  void clean() {
    awaitNoJob();
    jdbc.update("DELETE FROM sanction");
    jdbc.update("DELETE FROM sanction_intake_run");
    jdbc.update("DELETE FROM outbox_event");
    TestProviderService.reset();
    TestProviderService.holds(NPI, "A");
  }

  // ---- the calls --------------------------------------------------------------------------------

  /** POST of the bytes with this content type; {@code headers} are name and value pairs. */
  HttpResponse<String> post(String contentType, byte[] body, String... headers) throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/batch/sanction-intake-runs"))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer admin-token")
            .header("Content-Type", contentType)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body));
    for (int i = 0; i < headers.length; i += 2) {
      request.header(headers[i], headers[i + 1]);
    }
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  /** A well-formed multipart message: the business date and the file. */
  HttpResponse<String> upload(byte[] file) throws Exception {
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    body.writeBytes(ascii(BUSINESS_DATE_PART + FILE_PART_HEAD));
    body.writeBytes(file);
    body.writeBytes(ascii(CLOSING));
    return post(MULTIPART, body.toByteArray());
  }

  /** A file whose first record is 5 bytes, so a run that reads it fails at once; padded to size. */
  static byte[] unreadableFileOf(int size) {
    byte[] file = new byte[size];
    Arrays.fill(file, (byte) 'X');
    byte[] head = "short\n".getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(head, 0, file, 0, head.length);
    return file;
  }

  static byte[] ascii(String text) {
    return text.getBytes(StandardCharsets.US_ASCII);
  }

  int runRows() {
    return jdbc.queryForObject("SELECT count(*) FROM sanction_intake_run", Integer.class);
  }

  int jobExecutions() {
    return jdbc.queryForObject("SELECT count(*) FROM batch_job_execution", Integer.class);
  }

  /** The call is answered 400; anything else fails here, with the answer in the message. */
  HttpResponse<String> rejected(String contentType, byte[] body, String... headers)
      throws Exception {
    HttpResponse<String> r = post(contentType, body, headers);
    assertThat(r.statusCode()).as("answer " + r.statusCode() + " " + r.body()).isEqualTo(400);
    return r;
  }

  // ---- the web server's limit (Q-120, guards: pass today)
  // ------------------------------------------

  @Test
  @DisplayName(
      "[MAM-55 AC-1] a multipart upload of 0.9 MB, under every limit of the web server, reaches the real endpoint: 202 with a BatchRun (code review 2026-11-20, Q-143)")
  void fileUnderTheLimitIsAccepted() throws Exception {
    HttpResponse<String> r = upload(unreadableFileOf(UNDER_THE_LIMIT));
    assertThat(r.statusCode()).as(r.body()).isEqualTo(202);
    JsonNode run = json.readTree(r.body());
    assertThat(run.path("job").asText()).isEqualTo("PRVSAN01");
    assertThat(run.path("businessDate").asText()).isEqualTo("2026-03-31");
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-1] an upload over the web server's limit is still refused with 413 and no run is created: the mapping of unreadable bodies does not swallow it (Q-120)")
  void fileOverTheLimitIsStill413() throws Exception {
    int jobsBefore = jobExecutions();
    HttpResponse<String> r = upload(unreadableFileOf(OVER_THE_LIMIT));
    assertThat(r.statusCode()).as("answer " + r.body()).isEqualTo(413);
    assertThat(runRows()).isZero();
    assertThat(jobExecutions()).isEqualTo(jobsBefore);
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-1] after an upload over the limit the service still answers: the next upload under the limit is accepted")
  void serviceGoesOnAfterAnOversizeUpload() throws Exception {
    upload(unreadableFileOf(OVER_THE_LIMIT));
    HttpResponse<String> next = upload(unreadableFileOf(10_000));
    assertThat(next.statusCode()).as(next.body()).isEqualTo(202);
  }

  // ---- a body with no part, or without a required part (AC-1, guards: pass today)
  // ------------------

  static Stream<Arguments> bodiesWithoutARequiredPart() {
    return Stream.of(
        Arguments.of(
            "a body in which the boundary never appears", MULTIPART, ascii("not multipart")),
        Arguments.of("an empty body", MULTIPART, new byte[0]),
        Arguments.of(
            "a complete message without the file part",
            MULTIPART,
            ascii(BUSINESS_DATE_PART + "--" + BOUNDARY + "--\r\n")),
        Arguments.of(
            "a complete message without the businessDate part",
            MULTIPART,
            ascii(FILE_PART_HEAD + RECORD + CLOSING)));
  }

  @ParameterizedTest(
      name =
          "[MAM-55 AC-1] a start with {0} is a 400 application/problem+json and creates no run (code review 2026-11-20)")
  @MethodSource("bodiesWithoutARequiredPart")
  void bodyWithoutARequiredPartIsA400Problem(String what, String contentType, byte[] body)
      throws Exception {
    int jobsBefore = jobExecutions();
    HttpResponse<String> r = rejected(contentType, body);
    assertThat(r.headers().firstValue("Content-Type").orElse(""))
        .startsWith("application/problem+json");
    assertThat(runRows()).isZero();
    assertThat(jobExecutions()).isEqualTo(jobsBefore);
  }

  // ---- a body that cannot be read as a multipart message (lesson h of the code reviews)
  // ------------

  /** Bodies and content types that no multipart parser can read; the first two are the review's. */
  static Stream<Arguments> unreadableBodies() {
    return Stream.of(
        Arguments.of(
            "a content type without a boundary parameter",
            "multipart/form-data",
            ascii(BUSINESS_DATE_PART + FILE_PART_HEAD + RECORD + CLOSING)),
        Arguments.of(
            "a file part cut off before its closing boundary",
            MULTIPART,
            ascii(BUSINESS_DATE_PART + FILE_PART_HEAD + RECORD + "\r\n")),
        Arguments.of(
            "a file part whose headers are cut off",
            MULTIPART,
            ascii(
                BUSINESS_DATE_PART
                    + "--"
                    + BOUNDARY
                    + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"sancin.dat\"")),
        Arguments.of(
            "a businessDate part cut off",
            MULTIPART,
            ascii(
                "--"
                    + BOUNDARY
                    + "\r\nContent-Disposition: form-data; name=\"businessDate\"\r\n\r\n2026-03-")));
  }

  @ParameterizedTest(
      name =
          "[MAM-55 AC-1] a start with {0} is a 400 application/problem+json (code review 2026-11-20, Q-143)")
  @MethodSource("unreadableBodies")
  void unreadableMultipartBodyIsA400Problem(String what, String contentType, byte[] body)
      throws Exception {
    HttpResponse<String> r = rejected(contentType, body);
    assertThat(r.headers().firstValue("Content-Type").orElse(""))
        .startsWith("application/problem+json");
  }

  @ParameterizedTest(
      name =
          "[MAM-55 AC-1] a start with {0}: the problem has status 400 and the title Bad Request (code review 2026-11-20)")
  @MethodSource("unreadableBodies")
  void problemHasStatusAndTitle(String what, String contentType, byte[] body) throws Exception {
    JsonNode problem = json.readTree(rejected(contentType, body).body());
    assertThat(problem.path("status").asInt()).isEqualTo(400);
    assertThat(problem.path("title").asText()).isEqualTo("Bad Request");
  }

  @ParameterizedTest(
      name =
          "[MAM-55 AC-1] a start with {0}: the detail says that the upload could not be read (code review 2026-11-20)")
  @MethodSource("unreadableBodies")
  void detailSaysTheUploadCouldNotBeRead(String what, String contentType, byte[] body)
      throws Exception {
    JsonNode problem = json.readTree(rejected(contentType, body).body());
    assertThat(problem.path("detail").asText().toLowerCase())
        .contains("upload")
        .contains("could not be read");
  }

  @ParameterizedTest(
      name =
          "[MAM-55 AC-1] a start with {0}: the answer has no SQL and no class name and the detail is short (code review 2026-11-20)")
  @MethodSource("unreadableBodies")
  void answerNamesNeitherSqlNorClass(String what, String contentType, byte[] body)
      throws Exception {
    HttpResponse<String> r = rejected(contentType, body);
    assertThat(r.body().toLowerCase())
        .doesNotContain(
            "select",
            "insert",
            "update",
            "sql",
            "exception",
            "org.apache",
            "org.springframework",
            "java.",
            "tomcat",
            "fileupload",
            "stacktrace");
    assertThat(json.readTree(r.body()).path("detail").asText()).hasSizeBetween(1, 200);
  }

  @ParameterizedTest(
      name =
          "[MAM-55 AC-1] a start with {0}: the X-Correlation-Id of the call is echoed in the answer (code review 2026-11-20)")
  @MethodSource("unreadableBodies")
  void correlationIdOfTheCallIsEchoed(String what, String contentType, byte[] body)
      throws Exception {
    HttpResponse<String> r = rejected(contentType, body, "X-Correlation-Id", "upload-check-400");
    assertThat(r.headers().firstValue("X-Correlation-Id")).contains("upload-check-400");
  }

  @ParameterizedTest(
      name =
          "[MAM-55 AC-1] a start with {0} and no X-Correlation-Id: the answer carries a generated one (code review 2026-11-20)")
  @MethodSource("unreadableBodies")
  void correlationIdIsGeneratedWhenTheCallHasNone(String what, String contentType, byte[] body)
      throws Exception {
    HttpResponse<String> r = rejected(contentType, body);
    assertThat(r.headers().firstValue("X-Correlation-Id").orElse("")).matches(SAFE_ID);
  }

  @ParameterizedTest(
      name =
          "[MAM-55 AC-1] a start with {0} and Accept: application/json is still answered application/problem+json (code review 2026-11-20)")
  @MethodSource("unreadableBodies")
  void problemMediaTypeHoldsForAJsonClient(String what, String contentType, byte[] body)
      throws Exception {
    HttpResponse<String> r = rejected(contentType, body, "Accept", "application/json");
    assertThat(r.headers().firstValue("Content-Type").orElse(""))
        .startsWith("application/problem+json");
  }

  @ParameterizedTest(
      name =
          "[MAM-55 AC-1] a start with {0} creates no run: no run row and no Spring Batch execution (code review 2026-11-20)")
  @MethodSource("unreadableBodies")
  void unreadableBodyStartsNoRun(String what, String contentType, byte[] body) throws Exception {
    int jobsBefore = jobExecutions();
    rejected(contentType, body);
    assertThat(runRows()).isZero();
    assertThat(jobExecutions()).isEqualTo(jobsBefore);
  }

  @ParameterizedTest(
      name =
          "[MAM-55 AC-1] a start with {0} applies nothing: no provider-service call, no history row, no event (code review 2026-11-20)")
  @MethodSource("unreadableBodies")
  void unreadableBodyAppliesNothing(String what, String contentType, byte[] body) throws Exception {
    rejected(contentType, body);
    assertThat(TestProviderService.MOCK.findAll(anyRequestedFor(urlPathMatching("/api/v1/.*"))))
        .isEmpty();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM sanction", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event", Integer.class)).isZero();
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-1] after an unreadable body the service still answers: the next well-formed start is accepted (code review 2026-11-20)")
  void serviceGoesOnAfterAnUnreadableBody() throws Exception {
    post("multipart/form-data", ascii(BUSINESS_DATE_PART + FILE_PART_HEAD + RECORD + CLOSING));
    HttpResponse<String> next = upload(unreadableFileOf(10_000));
    assertThat(next.statusCode()).as(next.body()).isEqualTo(202);
  }
}
