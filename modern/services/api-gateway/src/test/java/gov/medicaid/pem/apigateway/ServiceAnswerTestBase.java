package gov.medicaid.pem.apigateway;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base of the gateway tests for what the services answer and receive (MAM-29, code review of
 * 2026-11-21): one gateway context for all of them, with the shipped {@code api-gateway.yml}
 * unchanged (default connect and read limits) and all nine services pointing to one {@link
 * AnsweringServiceStub}. The test client is a plain JDK HTTP client, so what a test sees is what a
 * caller of the gateway gets.
 *
 * <p>Every call has its own limit ({@link #CLIENT_LIMIT}) for the WHOLE answer, status line,
 * headers and body: a gateway that never answers, or announces a body that never arrives (the
 * failure of the 401 to a write, which {@code HttpRequest.timeout} would not catch: it ends at the
 * headers), makes the test FAIL with a message and never hang the build.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.config.name=api-gateway", "PEM_LOG_FORMAT="})
abstract class ServiceAnswerTestBase {

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

  static final String JSON = "application/json";

  /** A synthetic application and a synthetic rule table (no real provider data). */
  static final String APPLICATION_BODY =
      "{\"npi\":\"1003000111\",\"typeCode\":\"IN\",\"taxonomyCode\":\"3412000000\","
          + "\"lastName\":\"Mu\\u00f1oz\",\"firstName\":\"Zo\\u00eb\"}";

  static final String RULES_BODY = "{\"rules\":[{\"taxonomyPrefix\":\"341\",\"tier\":\"M\"}]}";

  /**
   * The test client gives up after this, for the whole answer: far above the time a local call
   * needs (milliseconds; 2 MB take well under a second), far below the 30 s guard of each test.
   */
  static final Duration CLIENT_LIMIT = Duration.ofSeconds(5);

  /** The first call through a fresh gateway pays for lazy start-up (HTTP client, thread pool). */
  private static final Duration WARM_UP_LIMIT = Duration.ofSeconds(20);

  /**
   * One stub for the whole JVM: the context is cached and shared by the test classes of this base,
   * so a class must not close it for the next one. A shutdown hook closes it.
   */
  static final AnsweringServiceStub STUB = AnsweringServiceStub.start();

  static {
    Runtime.getRuntime().addShutdownHook(new Thread(STUB::close));
  }

  private static final HttpClient CLIENT =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .connectTimeout(Duration.ofSeconds(5))
          .build();
  private static final AtomicInteger COUNTER = new AtomicInteger();
  private static final AtomicBoolean WARMED_UP = new AtomicBoolean();

  @DynamicPropertySource
  static void serviceUrls(DynamicPropertyRegistry registry) {
    for (String service : SERVICES) {
      registry.add("pem.gateway.services." + service, () -> "http://127.0.0.1:" + STUB.port());
    }
  }

  @LocalServerPort int port;

  /** One quick call keeps the start-up cost out of the timed limits of the tests. */
  @BeforeEach
  void warmUp() throws Exception {
    if (WARMED_UP.compareAndSet(false, true)) {
      call("GET", "/api/v1/providers/1003000111").send(WARM_UP_LIMIT);
    }
  }

  /** What the gateway answered. Header names are lower case. */
  record Answer(int status, Map<String, String> headers, byte[] body) {

    String header(String name) {
      return headers.getOrDefault(name.toLowerCase(Locale.ROOT), "");
    }

    String contentType() {
      return header("Content-Type");
    }

    String text() {
      return new String(body, StandardCharsets.UTF_8);
    }
  }

  Req call(String method, String path) {
    return new Req(method, path);
  }

  /**
   * One call through the gateway. Each has its own test id and correlation id, so the calls the
   * stub saw for it can be told from every other test's.
   */
  final class Req {
    private final String method;
    private final String path;
    private final String id = "r" + COUNTER.incrementAndGet();
    private final Map<String, String> headers = new LinkedHashMap<>();
    private byte[] body;

    private Req(String method, String path) {
      this.method = method;
      this.path = path;
      header(AnsweringServiceStub.TEST_ID_HEADER, id);
      header("X-Correlation-Id", correlationId());
    }

    String correlationId() {
      return "gw-" + id;
    }

    Req header(String name, String value) {
      headers.put(name, value);
      return this;
    }

    Req body(String text, String contentType) {
      return body(text.getBytes(StandardCharsets.UTF_8), contentType);
    }

    Req body(byte[] data, String contentType) {
      this.body = data;
      return header("Content-Type", contentType);
    }

    Answer send() throws Exception {
      return send(CLIENT_LIMIT);
    }

    private Answer send(Duration limit) throws Exception {
      HttpRequest.Builder request =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
              .method(
                  method,
                  body == null
                      ? HttpRequest.BodyPublishers.noBody()
                      : HttpRequest.BodyPublishers.ofByteArray(body));
      headers.forEach(request::header);
      AtomicReference<String> head = new AtomicReference<>("no status line and no headers arrived");
      HttpResponse.BodyHandler<byte[]> handler =
          info -> {
            head.set(
                "the status line and headers arrived ("
                    + info.statusCode()
                    + " "
                    + info.headers().map()
                    + ")");
            return HttpResponse.BodySubscribers.ofByteArray();
          };
      CompletableFuture<HttpResponse<byte[]>> pending = CLIENT.sendAsync(request.build(), handler);
      HttpResponse<byte[]> response;
      try {
        response = pending.get(limit.toMillis(), TimeUnit.MILLISECONDS);
      } catch (TimeoutException e) {
        pending.cancel(true);
        throw new AssertionError(
            "The gateway did not complete its answer (status, headers and body) within "
                + limit.toSeconds()
                + " s to "
                + method
                + " "
                + path
                + ": "
                + head.get()
                + ", the body did not complete",
            e);
      } catch (ExecutionException e) {
        throw new AssertionError(
            "The answer of the gateway to "
                + method
                + " "
                + path
                + " could not be read: "
                + e.getCause(),
            e.getCause());
      }
      Map<String, String> answered = new LinkedHashMap<>();
      response
          .headers()
          .map()
          .forEach(
              (name, values) ->
                  answered.put(name.toLowerCase(Locale.ROOT), String.join(",", values)));
      return new Answer(response.statusCode(), answered, response.body());
    }

    /**
     * The calls the stub received for this request: none, one, or more if the gateway repeated it.
     */
    List<AnsweringServiceStub.Call> serviceCalls() {
      return STUB.callsFor(id);
    }
  }
}
