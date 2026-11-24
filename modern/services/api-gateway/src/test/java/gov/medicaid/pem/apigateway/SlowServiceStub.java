package gov.medicaid.pem.apigateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The "slow service" of the gateway timeout tests (MAM-29 AC-6), the gateway's counterpart of the
 * kit's {@code SilentServer}: a service that accepts a call and then does not answer, so a gateway
 * without a read limit would wait for ever. It serves four kinds of path on the loopback address:
 *
 * <ul>
 *   <li>{@link #QUICK_PATH}: answers 200 at once (the warm-up call);
 *   <li>{@link #DOWNLOAD_PATH}: sends the response headers and the first part of the body at once,
 *       then {@link #DOWNLOAD_PARTS} parts in all, {@link #GAP_MILLIS} apart: a download that keeps
 *       flowing and takes longer in total (about 2 s) than the gateway's read limit in the tests (1
 *       s), although no single pause is as long as the limit;
 *   <li>{@link #STALLED_PATH}: sends the response headers and the first part, then stalls: the body
 *       never continues (held until {@link #close()});
 *   <li>every other path: reads the request and never answers. The call is held until {@link
 *       #close()} (at most 60 s, so a forgotten stub cannot keep a thread for ever).
 * </ul>
 *
 * It has its own thread per call, so one held call never blocks another.
 */
final class SlowServiceStub implements AutoCloseable {

  static final String QUICK_PATH = "/api/v1/providers/1003000111";
  static final String DOWNLOAD_PATH = "/api/v1/legacy/provider-master";
  static final String STALLED_PATH =
      "/api/v1/batch/revalidation-sweeps/00000000-0000-0000-0000-000000000001/queue-file";

  /** The parts of the flowing download; the whole body is these in order. */
  static final int DOWNLOAD_PARTS = 5;

  static final String DOWNLOAD_BODY = "PART-1|PART-2|PART-3|PART-4|PART-5|";

  /**
   * The pause between two parts: well below the read limit of the tests (1 s), so the download
   * never goes quiet for as long as the limit, but {@code DOWNLOAD_PARTS - 1} pauses add up to
   * about twice the limit.
   */
  static final long GAP_MILLIS = 500;

  private static final String STALLED_FIRST_PART = "FIRST-PART-ONLY|";

  private static final long HOLD_SECONDS = 60;

  private final HttpServer server;
  private final ExecutorService threads;
  private final CountDownLatch release = new CountDownLatch(1);
  private final List<String> seen = new CopyOnWriteArrayList<>();

  private SlowServiceStub(HttpServer server, ExecutorService threads) {
    this.server = server;
    this.threads = threads;
  }

  static SlowServiceStub start() {
    try {
      HttpServer server =
          HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
      ExecutorService threads =
          Executors.newCachedThreadPool(
              task -> {
                Thread thread = new Thread(task, "slow-service-stub");
                thread.setDaemon(true);
                return thread;
              });
      SlowServiceStub stub = new SlowServiceStub(server, threads);
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

  /** How many calls for this exact path the stub has received so far. */
  long callsTo(String path) {
    return seen.stream().filter(path::equals).count();
  }

  private void handle(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    seen.add(path);
    try (exchange) {
      if (path.equals(QUICK_PATH)) {
        byte[] body = "{\"service\":\"quick\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
      } else if (path.equals(DOWNLOAD_PATH)) {
        exchange.getResponseHeaders().add("Content-Type", "text/plain");
        exchange.sendResponseHeaders(200, 0);
        OutputStream out = exchange.getResponseBody();
        for (int part = 1; part <= DOWNLOAD_PARTS; part++) {
          out.write(("PART-" + part + "|").getBytes(StandardCharsets.UTF_8));
          out.flush();
          if (part < DOWNLOAD_PARTS) {
            sleep(GAP_MILLIS);
          }
        }
      } else if (path.equals(STALLED_PATH)) {
        exchange.getResponseHeaders().add("Content-Type", "text/plain");
        exchange.sendResponseHeaders(200, 0);
        OutputStream out = exchange.getResponseBody();
        out.write(STALLED_FIRST_PART.getBytes(StandardCharsets.UTF_8));
        out.flush();
        hold();
      } else {
        hold();
      }
    }
  }

  /** Says nothing: the connection stays open and unanswered until the stub is closed. */
  private void hold() {
    try {
      release.await(HOLD_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void close() {
    release.countDown();
    server.stop(0);
    threads.shutdownNow();
  }
}
