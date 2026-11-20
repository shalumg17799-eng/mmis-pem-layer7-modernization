package gov.medicaid.pem.enrollment.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.testsupport.PemContainers;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Q-120 (default applied: the implicit 2 MB upload limit stays): the intake files arrive as a
 * multipart upload and Tomcat's own limit of 2 MB applies, which MockMvc does not go through. This
 * test uses the real web server on a random port (the tokens are accepted by a test decoder). A
 * file under the limit is taken (202); a file over it is refused with a client error before any run
 * is created, and the service goes on answering. Every call has a time limit, so a service that
 * hangs on a large upload fails the test instead of the build. This class has a Spring context of
 * its own (real web server), closed after the class: the test JVM has a small heap.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.config.name=enrollment-service", "PEM_LOG_FORMAT="})
@ImportTestcontainers(PemContainers.class)
@Import(IntakeUploadLimitTest.AdminTokens.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class IntakeUploadLimitTest {

  static final String BOUNDARY = "----pem-test-boundary";
  static final int TWO_MB = 2 * 1024 * 1024;

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

  final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  @BeforeEach
  @AfterEach
  void clean() {
    Awaitility.await("no intake job is still running")
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () ->
                jdbc.queryForObject(
                        "SELECT count(*) FROM batch_job_execution"
                            + " WHERE status IN ('STARTING', 'STARTED')",
                        Integer.class)
                    == 0);
    jdbc.update("DELETE FROM application_error");
    jdbc.update("DELETE FROM application");
    jdbc.update("DELETE FROM intake_run");
    jdbc.update("DELETE FROM outbox_event");
  }

  /** A file whose first record is 5 bytes, so the run fails at once; padded to {@code size}. */
  static byte[] unreadableFileOf(int size) {
    byte[] file = new byte[size];
    java.util.Arrays.fill(file, (byte) 'X');
    byte[] head = "short\n".getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(head, 0, file, 0, head.length);
    return file;
  }

  HttpResponse<String> upload(byte[] file) throws Exception {
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    body.writeBytes(
        ("--"
                + BOUNDARY
                + "\r\nContent-Disposition: form-data; name=\"businessDate\"\r\n\r\n2026-03-31\r\n"
                + "--"
                + BOUNDARY
                + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"appinput.dat\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n")
            .getBytes(StandardCharsets.US_ASCII));
    body.writeBytes(file);
    body.writeBytes(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII));
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/batch/intake-runs"))
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", "Bearer admin-token")
            .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
            .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  @Test
  @DisplayName(
      "[Q-120] [MAM-56 AC-1] a multipart upload of 1.5 MB, under the 2 MB limit, reaches the real endpoint: 202 with a BatchRun")
  void fileUnderTheLimitIsAccepted() throws Exception {
    HttpResponse<String> r = upload(unreadableFileOf(1_500_000));
    assertThat(r.statusCode()).as(r.body()).isEqualTo(202);
    JsonNode run = json.readTree(r.body());
    assertThat(run.path("job").asText()).isEqualTo("PRVENR01");
    assertThat(run.path("businessDate").asText()).isEqualTo("2026-03-31");
  }

  @Test
  @DisplayName(
      "[Q-120] [MAM-56 AC-1] an upload over the 2 MB limit is refused with a client error and no run is created")
  void fileOverTheLimitIsRefused() throws Exception {
    HttpResponse<String> r = upload(unreadableFileOf(TWO_MB + 100_000));
    assertThat(r.statusCode()).as("answer " + r.body()).isBetween(400, 499);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM intake_run", Integer.class)).isZero();
  }

  @Test
  @DisplayName(
      "[Q-120] [MAM-56 AC-1] after an upload over the limit the service still answers: the next upload under the limit is accepted")
  void serviceGoesOnAfterAnOversizeUpload() throws Exception {
    upload(unreadableFileOf(TWO_MB + 100_000));
    HttpResponse<String> next = upload(unreadableFileOf(10_000));
    assertThat(next.statusCode()).as(next.body()).isEqualTo(202);
  }
}
