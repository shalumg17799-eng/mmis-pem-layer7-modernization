package gov.medicaid.pem.apigateway;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.http.HttpStatus;

/**
 * The "service that answers" of the gateway relay tests (MAM-29, code review of 2026-11-21: a
 * service's 401 to a POST, PUT or DELETE reached the caller with an empty body). Unlike {@link
 * SlowServiceStub} it always answers, and it answers as a real service of the estate does: a
 * problem+json body for an error, and for a 401 the {@code WWW-Authenticate: Bearer} challenge (the
 * shape of the kit's {@code BearerProblemHandler}).
 *
 * <p>It records every call it receives (method, path, headers, body) and is told what to do by
 * headers of the request, so one stub serves every test:
 *
 * <ul>
 *   <li>{@link #STATUS_HEADER}: the status to answer (default 200). 202 carries a {@code Location}
 *       header; 400 and above a problem+json body ({@link #problemBody(int, String)});
 *   <li>{@link #DOWNLOAD_HEADER}: answer 200 with that many bytes ({@link #download(int)}) as a
 *       file download with a {@code Content-Disposition}; {@link #CHUNKED_HEADER} {@code true}
 *       sends them chunked, otherwise with a {@code Content-Length};
 *   <li>{@link #DROP_HEADER}: read the whole request, then close the connection without a word (a
 *       service that crashes or restarts while it handles the call).
 * </ul>
 *
 * Calls are looked up by the {@link #TEST_ID_HEADER} of the request, so a test sees its own calls
 * only.
 */
final class AnsweringServiceStub implements AutoCloseable {

  static final String STATUS_HEADER = "X-Stub-Status";
  static final String DOWNLOAD_HEADER = "X-Stub-Download-Bytes";
  static final String CHUNKED_HEADER = "X-Stub-Chunked";
  static final String DROP_HEADER = "X-Stub-Drop";
  static final String TEST_ID_HEADER = "X-Test-Id";

  static final String LOCATION =
      "/api/v1/batch/revalidation-sweeps/00000000-0000-0000-0000-000000000001";
  static final String DISPOSITION = "attachment; filename=\"provider-master.dat\"";

  /** What the stub received. Header names are lower case. */
  record Call(String method, String path, Map<String, String> headers, byte[] body) {

    String header(String name) {
      return headers.get(name.toLowerCase(Locale.ROOT));
    }

    String bodyText() {
      return new String(body, StandardCharsets.UTF_8);
    }
  }

  private final HttpServer server;
  private final ExecutorService threads;
  private final List<Call> calls = new CopyOnWriteArrayList<>();

  private AnsweringServiceStub(HttpServer server, ExecutorService threads) {
    this.server = server;
    this.threads = threads;
  }

  static AnsweringServiceStub start() {
    try {
      HttpServer server =
          HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
      ExecutorService threads =
          Executors.newCachedThreadPool(
              task -> {
                Thread thread = new Thread(task, "answering-service-stub");
                thread.setDaemon(true);
                return thread;
              });
      AnsweringServiceStub stub = new AnsweringServiceStub(server, threads);
      server.setExecutor(threads);
      server.createContext("/", stub::handle);
      server.start();
      return stub;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  int port() {
    return server.getAddress().getPort();
  }

  /** The calls that carried this {@link #TEST_ID_HEADER}, in the order they arrived. */
  List<Call> callsFor(String testId) {
    return calls.stream().filter(call -> testId.equals(call.header(TEST_ID_HEADER))).toList();
  }

  /** The problem+json body the stub answers for a status (the caller must get it back intact). */
  static String problemBody(int status, String path) {
    String detail =
        status == 401 ? "A valid bearer token is required." : "The stub answered " + status + ".";
    return "{\"type\":\"about:blank\",\"title\":\""
        + HttpStatus.valueOf(status).getReasonPhrase()
        + "\",\"status\":"
        + status
        + ",\"detail\":\""
        + detail
        + "\",\"instance\":\""
        + path
        + "\"}";
  }

  /** The bytes of a download of this size: the same every time, so a test can compare them. */
  static byte[] download(int size) {
    byte[] out = new byte[size];
    for (int i = 0; i < size; i++) {
      out[i] = (byte) ('A' + (i + i / 130) % 26);
    }
    return out;
  }

  private void handle(HttpExchange exchange) throws IOException {
    try (exchange) {
      byte[] body = exchange.getRequestBody().readAllBytes();
      String path = exchange.getRequestURI().getPath();
      Headers in = exchange.getRequestHeaders();
      calls.add(new Call(exchange.getRequestMethod(), path, lowerCase(in), body));
      if (in.getFirst(DROP_HEADER) != null) {
        return; // closed without a response
      }
      String size = in.getFirst(DOWNLOAD_HEADER);
      if (size != null) {
        sendDownload(exchange, Integer.parseInt(size), "true".equals(in.getFirst(CHUNKED_HEADER)));
        return;
      }
      int status =
          in.getFirst(STATUS_HEADER) == null ? 200 : Integer.parseInt(in.getFirst(STATUS_HEADER));
      send(exchange, status, path);
    }
  }

  private static void sendDownload(HttpExchange exchange, int size, boolean chunked)
      throws IOException {
    byte[] payload = download(size);
    exchange.getResponseHeaders().add("Content-Type", "text/plain");
    exchange.getResponseHeaders().add("Content-Disposition", DISPOSITION);
    exchange.sendResponseHeaders(200, chunked ? 0 : payload.length);
    exchange.getResponseBody().write(payload);
  }

  private static void send(HttpExchange exchange, int status, String path) throws IOException {
    Headers out = exchange.getResponseHeaders();
    String type = "application/json";
    String text = "{\"service\":\"stub\"}";
    if (status >= 400) {
      type = "application/problem+json";
      text = problemBody(status, path);
    }
    if (status == 401) {
      out.add("WWW-Authenticate", "Bearer");
    }
    if (status == 202) {
      out.add("Location", LOCATION);
      text = "{\"id\":\"00000000-0000-0000-0000-000000000001\",\"status\":\"ACCEPTED\"}";
    }
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    out.add("Content-Type", type);
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
  }

  private static Map<String, String> lowerCase(Headers headers) {
    Map<String, String> out = new LinkedHashMap<>();
    headers.forEach(
        (name, values) -> out.put(name.toLowerCase(Locale.ROOT), String.join(",", values)));
    return out;
  }

  @Override
  public void close() {
    server.stop(0);
    threads.shutdownNow();
  }
}
