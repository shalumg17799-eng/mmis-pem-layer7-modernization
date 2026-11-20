package gov.medicaid.pem.revalidation.api;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import gov.medicaid.pem.revalidation.RevalidationFixtures;
import gov.medicaid.pem.testsupport.PemContainers;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;
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
 * A request that fails on a database error (lesson a of the earlier reviews, D-024; MAM-45 AC-8 and
 * MAM-48 AC-2): the caller sees a failure (5xx), never a success; what the caller reads is short
 * (at most 200 characters) and holds no SQL text, table name or driver message; the work is rolled
 * back; and the whole exception, with its database message, is logged at ERROR.
 *
 * <p>MockMvc hands an unhandled exception to the test instead of answering, so it cannot show what
 * a caller of the real service receives. This class therefore uses the real web server on a random
 * port (a test decoder accepts the tokens "admin-token" and "analyst-token"). The database failure
 * is a trigger that rejects one kind of write, lifted after every test.
 *
 * <p>This class has a Spring context of its own, closed after the class (the test JVM has a small
 * heap). The shared Postgres and Kafka containers are wired through properties, not registered as
 * beans: a context stops the container beans it owns when it closes, which would stop the
 * containers that the other test classes still use.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.config.name=revalidation-service", "PEM_LOG_FORMAT="})
@Import(RequestFailureTest.Tokens.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Timeout(value = 4, unit = TimeUnit.MINUTES)
class RequestFailureTest {

  static final String[] SQL_WORDS = {"select", "insert", "update", "sql", "revalidation_case"};

  @DynamicPropertySource
  static void sharedContainers(DynamicPropertyRegistry registry) {
    PemContainers.POSTGRES.start();
    PemContainers.KAFKA.start();
    registry.add("spring.datasource.url", PemContainers.POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", PemContainers.POSTGRES::getUsername);
    registry.add("spring.datasource.password", PemContainers.POSTGRES::getPassword);
    registry.add("spring.kafka.bootstrap-servers", PemContainers.KAFKA::getBootstrapServers);
  }

  /** The token value names the role: "admin-token" is an ADMIN, "analyst-token" an ANALYST. */
  @TestConfiguration
  static class Tokens {
    @Bean
    @Primary
    JwtDecoder roleDecoder() {
      return token ->
          Jwt.withTokenValue(token)
              .header("alg", "none")
              .subject("sub-" + token)
              .claim("preferred_username", token)
              .claim(
                  "realm_access",
                  Map.of("roles", List.of(token.startsWith("admin") ? "ADMIN" : "ANALYST")))
              .issuedAt(Instant.now())
              .expiresAt(Instant.now().plusSeconds(3600))
              .build();
    }
  }

  /** One log line as it was written. */
  record Logged(String level, String message, String throwable) {}

  static final class Capture extends AppenderBase<ILoggingEvent> {
    final List<Logged> lines = new CopyOnWriteArrayList<>();

    @Override
    protected void append(ILoggingEvent event) {
      lines.add(
          new Logged(
              event.getLevel().toString(),
              event.getFormattedMessage(),
              event.getThrowableProxy() == null
                  ? ""
                  : ThrowableProxyUtil.asString(event.getThrowableProxy())));
    }
  }

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;

  final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
  final Capture capture = new Capture();

  @BeforeEach
  void startCapture() {
    RevalidationFixtures.reset(jdbc);
    capture.setContext(root.getLoggerContext());
    capture.start();
    root.addAppender(capture);
  }

  @AfterEach
  void cleanUp() {
    root.detachAppender(capture);
    capture.stop();
    RevalidationFixtures.reset(jdbc);
  }

  private HttpResponse<String> post(String path, String token, String body) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private void assertReadsAsAShortFailure(HttpResponse<String> r) {
    assertThat(r.statusCode()).as("a failure the caller can see: %s", r.body()).isIn(500, 503);
    assertThat(r.body()).as("what the caller reads").hasSizeLessThanOrEqualTo(200);
    assertThat(r.body().toLowerCase())
        .doesNotContain(SQL_WORDS)
        .doesNotContain("synthetic")
        .doesNotContain("postgres")
        .doesNotContain("jdbc")
        .doesNotContain("org.springframework");
  }

  private void assertWholeExceptionIsLogged() {
    assertThat(capture.lines)
        .as("an ERROR line that carries the exception with the database's own message")
        .anySatisfy(
            l -> {
              assertThat(l.level()).isEqualTo(Level.ERROR.toString());
              assertThat(l.throwable()).as("an exception is attached").isNotBlank();
              assertThat(l.message() + " " + l.throwable()).contains("SYNTHETIC DISK FULL");
            });
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-2] a database failure while a case is completed is a short 5xx for the caller, rolls the case back and logs the whole exception")
  void failedCompletion() throws Exception {
    UUID caseId =
        RevalidationFixtures.insertCase(jdbc, "1003000222", LocalDate.of(2026, 2, 28), "DUE");
    RevalidationFixtures.reject(
        jdbc, "completion", "UPDATE", "revalidation_case", "true", "SYNTHETIC DISK FULL");

    HttpResponse<String> r =
        post(
            "/api/v1/revalidations/" + caseId + "/completion",
            "analyst-token",
            "{\"screeningId\":\"" + UUID.randomUUID() + "\",\"result\":\"PASS\"}");

    assertReadsAsAShortFailure(r);
    assertThat(RevalidationFixtures.caseStatus(jdbc, caseId))
        .as("the case is not half completed")
        .isEqualTo("DUE");
    assertWholeExceptionIsLogged();
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-8] a database failure while the sweep is started is a short 5xx for the caller: no run is recorded as started and no job begins")
  void failedSweepStart() throws Exception {
    int jobsBefore =
        RevalidationFixtures.count(
            jdbc, "SELECT count(*) FROM batch_job_instance WHERE job_name = 'revalidation-sweep'");
    RevalidationFixtures.reject(
        jdbc, "start", "INSERT", "sweep_run", "true", "SYNTHETIC DISK FULL");

    HttpResponse<String> r =
        post(
            "/api/v1/batch/revalidation-sweeps",
            "admin-token",
            "{\"businessDate\":\"2026-03-31\"}");

    assertReadsAsAShortFailure(r);
    assertThat(RevalidationFixtures.count(jdbc, "SELECT count(*) FROM sweep_run")).isZero();
    assertThat(
            RevalidationFixtures.count(
                jdbc,
                "SELECT count(*) FROM batch_job_instance WHERE job_name = 'revalidation-sweep'"))
        .isEqualTo(jobsBefore);
    assertWholeExceptionIsLogged();
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-2] with the database healthy again the same request answers normally: the failure left nothing behind")
  void recoversAfterTheFailure() throws Exception {
    UUID caseId =
        RevalidationFixtures.insertCase(jdbc, "1003000222", LocalDate.of(2026, 2, 28), "DUE");
    RevalidationFixtures.reject(
        jdbc, "completion", "UPDATE", "revalidation_case", "true", "SYNTHETIC DISK FULL");
    String body = "{\"screeningId\":\"" + UUID.randomUUID() + "\",\"result\":\"PASS\"}";
    post("/api/v1/revalidations/" + caseId + "/completion", "analyst-token", body);
    RevalidationFixtures.dropFailures(jdbc);

    HttpResponse<String> again =
        post("/api/v1/revalidations/" + caseId + "/completion", "analyst-token", body);

    assertThat(again.statusCode()).as(again.body()).isEqualTo(200);
    assertThat(RevalidationFixtures.caseStatus(jdbc, caseId)).isEqualTo("COMPLETED");
  }
}
