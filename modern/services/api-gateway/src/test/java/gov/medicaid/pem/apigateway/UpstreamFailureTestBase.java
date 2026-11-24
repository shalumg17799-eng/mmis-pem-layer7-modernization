package gov.medicaid.pem.apigateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.kit.web.CorrelationId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base of the gateway tests for services that fail (MAM-29 AC-6, D-024): one gateway context for
 * all of them, with the nine services set up as follows.
 *
 * <ul>
 *   <li>{@code tmsis} is stopped: nothing listens on its port (the call is refused);
 *   <li>the other eight point to one {@link SlowServiceStub}: it accepts a call and never answers,
 *       except for its quick path and its two downloads (one keeps flowing, one stalls).
 * </ul>
 *
 * The gateway's read limit is set to 1 s through the environment variable that the shipped {@code
 * api-gateway.yml} reads for it ({@code PEM_GATEWAY_READ_TIMEOUT}, named in the code review of
 * 2026-11-20 and in the red report of MAM-29), the same way as {@code PEM_LOG_FORMAT} is set. The
 * shipped default (30 s) would make every test wait that long.
 *
 * <p>Every call of a test has its own limit ({@link #CLIENT_LIMIT}): a gateway that never answers
 * makes the test FAIL with a message and never hang the build.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.config.name=api-gateway",
      "PEM_LOG_FORMAT=",
      "PEM_GATEWAY_READ_TIMEOUT=1s"
    })
abstract class UpstreamFailureTestBase {

  static final List<String> SERVICES =
      List.of(
          "provider",
          "enrollment",
          "screening",
          "risk",
          "revalidation",
          "sanctions",
          "notification",
          "directory",
          "tmsis");

  /** The service whose port has nothing listening. */
  static final String STOPPED = "tmsis";

  /** The read limit the tests configure, so the expected time to answer is about this. */
  static final Duration READ_LIMIT = Duration.ofSeconds(1);

  /** The test client gives up after this: far above the read limit, far below the 30 s guard. */
  static final Duration CLIENT_LIMIT = Duration.ofSeconds(6);

  /**
   * One stub for the whole JVM: the context is cached and shared by the test classes of this base,
   * so a class must not close it for the next one. A shutdown hook closes it.
   */
  static final SlowServiceStub STUB = SlowServiceStub.start();

  static final int STOPPED_PORT = freePort();

  static {
    Runtime.getRuntime().addShutdownHook(new Thread(STUB::close));
  }

  private static final HttpClient CLIENT =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .connectTimeout(Duration.ofSeconds(5))
          .build();
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final AtomicBoolean WARMED_UP = new AtomicBoolean();

  @DynamicPropertySource
  static void serviceUrls(DynamicPropertyRegistry registry) {
    for (String service : SERVICES) {
      int port = service.equals(STOPPED) ? STOPPED_PORT : STUB.port();
      registry.add("pem.gateway.services." + service, () -> "http://127.0.0.1:" + port);
    }
  }

  @LocalServerPort int port;

  /** What the gateway answered, and how long it took. */
  record Answer(
      int status, String contentType, String correlationId, String body, Duration elapsed) {

    JsonNode json() {
      try {
        return MAPPER.readTree(body);
      } catch (IOException e) {
        throw new AssertionError("the answer is not JSON: " + body, e);
      }
    }
  }

  /**
   * The first call through a fresh gateway pays for lazy start-up (HTTP client, thread pool); one
   * quick call keeps that out of the timed calls.
   */
  @BeforeEach
  void warmUp() throws Exception {
    if (WARMED_UP.compareAndSet(false, true)) {
      assertThat(get(SlowServiceStub.QUICK_PATH, Map.of()).status())
          .as("warm-up call through the gateway to the quick path of the stub")
          .isEqualTo(200);
    }
  }

  Answer get(String path, Map<String, String> headers) throws Exception {
    return send("GET", path, headers, null);
  }

  Answer send(String method, String path, Map<String, String> headers, String body)
      throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(CLIENT_LIMIT)
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
    headers.forEach(request::header);
    if (body != null) {
      request.header("Content-Type", "application/json");
    }
    long started = System.nanoTime();
    HttpResponse<String> response;
    try {
      response = CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    } catch (HttpTimeoutException e) {
      throw new AssertionError(
          "The gateway gave no answer within "
              + CLIENT_LIMIT.toSeconds()
              + " s to "
              + method
              + " "
              + path
              + " although its read limit is "
              + READ_LIMIT.toSeconds()
              + " s: a call to a service that never answers has no limit",
          e);
    }
    return new Answer(
        response.statusCode(),
        response.headers().firstValue("Content-Type").orElse(""),
        response.headers().firstValue(CorrelationId.HEADER).orElse(""),
        response.body(),
        Duration.ofNanos(System.nanoTime() - started));
  }

  private static int freePort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
