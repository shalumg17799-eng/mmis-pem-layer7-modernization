package gov.medicaid.pem.kit.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A real HTTP server on the loopback address for the client tests: {@code /token} answers like the
 * Keycloak token endpoint, {@code /echo} answers at once and remembers the Authorization and
 * X-Correlation-Id headers it received, {@code /slow} answers after the delay given to the
 * constructor.
 */
public final class StubHttpServer implements AutoCloseable {

  private final HttpServer server;
  private final ExecutorService executor =
      Executors.newFixedThreadPool(
          2,
          task -> {
            Thread thread = new Thread(task, "stub-http-server");
            thread.setDaemon(true);
            return thread;
          });
  private final AtomicReference<String> authorization = new AtomicReference<>();
  private final AtomicReference<String> correlationId = new AtomicReference<>();

  public StubHttpServer(Duration slowAnswerDelay) throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/token", exchange -> reply(exchange, "{\"access_token\":\"tok-1\",\"expires_in\":300}"));
    server.createContext(
        "/echo",
        exchange -> {
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          correlationId.set(exchange.getRequestHeaders().getFirst("X-Correlation-Id"));
          reply(exchange, "{\"ok\":true}");
        });
    server.createContext(
        "/slow",
        exchange -> {
          try {
            Thread.sleep(slowAnswerDelay.toMillis());
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
          }
          reply(exchange, "{\"ok\":true}");
        });
    server.setExecutor(executor);
    server.start();
  }

  private static void reply(HttpExchange exchange, String json) throws IOException {
    exchange.getRequestBody().readAllBytes();
    byte[] body = json.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, body.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(body);
    }
  }

  /** The base URL, for example {@code http://127.0.0.1:49153}. */
  public String url() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  /** The Authorization header of the last {@code /echo} request, or null. */
  public String lastAuthorization() {
    return authorization.get();
  }

  /** The X-Correlation-Id header of the last {@code /echo} request, or null. */
  public String lastCorrelationId() {
    return correlationId.get();
  }

  @Override
  public void close() {
    server.stop(0);
    executor.shutdownNow();
  }
}
