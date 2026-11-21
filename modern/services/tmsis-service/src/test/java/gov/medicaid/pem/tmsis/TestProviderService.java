package gov.medicaid.pem.tmsis;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * One fake provider-service for the whole test JVM (a JDK HTTP server, no extra library): it
 * answers GET /api/v1/providers as the contract's searchProviders does (the {@code status} filter,
 * {@code page} and {@code size}, the page of {@code items} with {@code total}) and plays the
 * Keycloak token endpoint that gives tmsis-service its SYSTEM token. Every request is recorded, and
 * the answer can be made to fail: an error status, a connection that is cut, an answer that never
 * comes in time, a body that is no page.
 *
 * <p>Contract 1.2.0 (Q-132, code review of tmsis-service, MUST-FIX 1): the search also pages by
 * key, exactly as the contract says. With the optional {@code afterNpi} (exactly 10 digits, given
 * once, {@code page} absent or 0; anything else is a 400 problem) the answer holds the matches
 * whose NPI is greater than the key, in ascending NPI order, at most {@code size} of them, with
 * page 0; an empty list means there is nothing after the key. {@code total} always counts ALL
 * matches of the filters, never only those after the key, and it is the number at the moment of the
 * answer: the data can be changed between two answers ({@link #afterSearch}), as an approval or a
 * termination at month end changes it for a real run. A search without {@code afterNpi} answers as
 * in 1.1.0 (the first call of a keyset walk is such a search).
 */
public final class TestProviderService {

  public static final String SYSTEM_TOKEN = "test-system-token";

  /** One provider as provider-service holds it: only the fields tmsis-service reads. */
  public record Prov(String npi, String medicaidId, String status, String effectiveDate) {

    /** An ACTIVE provider whose Medicaid ID is "M00000" + the last 3 digits of the NPI. */
    public static Prov active(String npi) {
      return of(npi, "A");
    }

    public static Prov of(String npi, String status) {
      return new Prov(npi, "M00000" + npi.substring(7), status, "2026-01-15");
    }

    public Prov effective(String date) {
      return new Prov(npi, medicaidId, status, date);
    }

    public Prov medicaid(String id) {
      return new Prov(npi, id, status, effectiveDate);
    }

    public Prov withStatus(String newStatus) {
      return new Prov(npi, medicaidId, newStatus, effectiveDate);
    }
  }

  /** One request the fake has seen. */
  public record Request(
      String method,
      String path,
      Map<String, String> query,
      String authorization,
      String correlationId) {}

  /** What the fake does instead of answering normally. */
  public enum Fault {
    /** 500 problem+json */
    SERVER_ERROR,
    /** the connection is closed without any answer */
    CONNECTION_CUT,
    /** 200 with a JSON object that is no provider page */
    NOT_A_PAGE,
    /** 200 with text that is not JSON */
    NOT_JSON,
    /** the answer comes only after {@link #SLOW_MILLIS}, far beyond any client timeout */
    SLOW
  }

  /** Long enough to be beyond every timeout, short enough not to hold a thread for ever. */
  public static final int SLOW_MILLIS = 15_000;

  private static final HttpServer SERVER;
  private static final List<Prov> PROVIDERS = new CopyOnWriteArrayList<>();
  private static final List<Request> REQUESTS = new CopyOnWriteArrayList<>();
  private static volatile boolean honourStatusFilter = true;
  private static volatile boolean descending;
  private static volatile Fault providerFault;
  private static volatile int faultFromPage;
  private static volatile Fault tokenFault;
  private static volatile int answerDelayMillis;
  private static final Map<Integer, Runnable> AFTER_SEARCH = new ConcurrentHashMap<>();
  private static volatile boolean ignoreTheKey;
  private static final AtomicInteger IGNORED_KEYS = new AtomicInteger();

  /**
   * A provider-service that ignores afterNpi answers at most this many keyset searches with the
   * first page again, then with an empty list, so that a reader that does not notice cannot loop
   * for ever.
   */
  private static final int MAX_IGNORED_KEYS = 3;

  static {
    try {
      SERVER = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    } catch (IOException e) {
      throw new IllegalStateException("cannot start the fake provider-service", e);
    }
    SERVER.setExecutor(
        Executors.newCachedThreadPool(
            r -> {
              Thread t = new Thread(r, "fake-provider-service");
              t.setDaemon(true);
              return t;
            }));
    SERVER.createContext("/token", TestProviderService::token);
    SERVER.createContext("/api/v1/providers", TestProviderService::providers);
    SERVER.start();
  }

  private TestProviderService() {}

  public static String baseUrl() {
    return "http://127.0.0.1:" + SERVER.getAddress().getPort();
  }

  public static void register(DynamicPropertyRegistry r) {
    r.add("pem.services.provider-url", TestProviderService::baseUrl);
    r.add("pem.client.token-uri", () -> baseUrl() + "/token");
    r.add("pem.client.client-secret", () -> "test-secret");
  }

  /** Forgets every provider, request and fault. */
  public static void reset() {
    PROVIDERS.clear();
    REQUESTS.clear();
    honourStatusFilter = true;
    descending = false;
    providerFault = null;
    faultFromPage = 0;
    tokenFault = null;
    answerDelayMillis = 0;
    AFTER_SEARCH.clear();
    ignoreTheKey = false;
    IGNORED_KEYS.set(0);
  }

  /** These providers, and no others. */
  public static void holds(Prov... providers) {
    holds(List.of(providers));
  }

  public static void holds(List<Prov> providers) {
    PROVIDERS.clear();
    PROVIDERS.addAll(providers);
  }

  /**
   * The fake answers every search with all its providers whatever the {@code status} filter says,
   * so that a test can show that tmsis-service itself reports only ACTIVE providers.
   */
  public static void ignoreStatusFilter() {
    honourStatusFilter = false;
  }

  /**
   * The fake lists its providers in descending NPI order, with or without a key: a breach of
   * contract 1.2.0, which promises ascending NPI order. A keyset walk cannot work on such answers,
   * so the extract must end the run FAILED instead of writing a file from them.
   */
  public static void answerInDescendingOrder() {
    descending = true;
  }

  /** Every search for page {@code fromPage} or later fails in this way (0 = from the first). */
  public static void providerFails(Fault fault, int fromPage) {
    faultFromPage = fromPage;
    providerFault = fault;
  }

  public static void providerFails(Fault fault) {
    providerFails(fault, 0);
  }

  /** The searches are answered normally again. */
  public static void providerRecovers() {
    providerFault = null;
  }

  /**
   * Every search is answered normally but only after this many milliseconds (a slow
   * provider-service that still answers within the client's timeout).
   */
  public static void answerSlowlyBy(int millis) {
    answerDelayMillis = millis;
  }

  /**
   * After the {@code n}-th search of the test (1 = the first) has been answered, and before the
   * next one can be, {@code change} runs: the provider data changes while a run is reading it. The
   * answer of that search is already fixed, so it shows the data as it was. Several changes for the
   * same search run in the order they were registered.
   */
  public static void afterSearch(int n, Runnable change) {
    AFTER_SEARCH.merge(
        n,
        change,
        (first, second) ->
            () -> {
              first.run();
              second.run();
            });
  }

  /** The provider gets this status at once (an approval: "A"; a termination: "T"). */
  public static void changeStatus(String npi, String status) {
    PROVIDERS.replaceAll(p -> p.npi().equals(npi) ? p.withStatus(status) : p);
  }

  /** One more provider, at once. */
  public static void add(Prov provider) {
    PROVIDERS.add(provider);
  }

  /**
   * The fake answers every search that has an {@code afterNpi} as a provider-service before 1.2.0
   * would, which does not know the parameter and ignores it: the first {@code size} matches again.
   * After {@value #MAX_IGNORED_KEYS} such answers it answers an empty list, so that a defective
   * reader ends and does not loop for ever.
   */
  public static void answerIgnoringTheKey() {
    ignoreTheKey = true;
  }

  /** The token endpoint fails in this way. */
  public static void tokenFails(Fault fault) {
    tokenFault = fault;
  }

  /** The searches seen so far, in order. */
  public static List<Request> searches() {
    return REQUESTS.stream().filter(r -> r.path().equals("/api/v1/providers")).toList();
  }

  /** The number of searches seen so far. */
  public static int searchCount() {
    return searches().size();
  }

  // ---- the two endpoints ------------------------------------------------------------------------

  private static void token(HttpExchange x) throws IOException {
    try {
      if (tokenFault != null && fault(x, tokenFault)) {
        return;
      }
      send(
          x,
          200,
          "application/json",
          "{\"access_token\":\""
              + SYSTEM_TOKEN
              + "\",\"token_type\":\"Bearer\",\"expires_in\":300}");
    } catch (IOException e) {
      // the client gave up (a timeout): nothing more to do
    } finally {
      x.close();
    }
  }

  private static void providers(HttpExchange x) {
    try {
      String raw = x.getRequestURI().getRawQuery();
      Map<String, String> query = query(raw);
      REQUESTS.add(
          new Request(
              x.getRequestMethod(),
              x.getRequestURI().getPath(),
              query,
              x.getRequestHeaders().getFirst("Authorization"),
              x.getRequestHeaders().getFirst("X-Correlation-Id")));
      int number = searchCount();
      String invalid = invalid(raw, query);
      if (invalid != null) {
        send(
            x,
            400,
            "application/problem+json",
            "{\"title\":\"Bad Request\",\"status\":400,\"detail\":\"" + invalid + "\"}");
        return;
      }
      int page = Integer.parseInt(query.getOrDefault("page", "0"));
      int size = Integer.parseInt(query.getOrDefault("size", "20"));
      String after = query.get("afterNpi");
      List<Prov> matching =
          PROVIDERS.stream()
              .filter(
                  p ->
                      !honourStatusFilter
                          || !query.containsKey("status")
                          || p.status().equals(query.get("status")))
              .sorted(
                  descending
                      ? Comparator.comparing(Prov::npi).reversed()
                      : Comparator.comparing(Prov::npi))
              .toList();
      // the page that an offset walk would be reading at this position (for a fault from a page on)
      int position =
          after == null
              ? page
              : (int) (matching.stream().filter(p -> p.npi().compareTo(after) <= 0).count() / size);
      if (providerFault != null && position >= faultFromPage && fault(x, providerFault)) {
        return;
      }
      if (answerDelayMillis > 0) {
        Thread.sleep(answerDelayMillis);
      }
      List<Prov> items;
      if (after == null) {
        int from = Math.min(page * size, matching.size());
        items = matching.subList(from, Math.min(from + size, matching.size()));
      } else if (ignoreTheKey) {
        items =
            IGNORED_KEYS.incrementAndGet() <= MAX_IGNORED_KEYS
                ? matching.subList(0, Math.min(size, matching.size()))
                : List.of();
      } else {
        items = matching.stream().filter(p -> p.npi().compareTo(after) > 0).limit(size).toList();
      }
      String body =
          "{\"items\":["
              + items.stream().map(TestProviderService::json).collect(Collectors.joining(","))
              + "],\"page\":"
              + (after == null ? page : 0)
              + ",\"size\":"
              + size
              + ",\"total\":"
              + matching.size()
              + "}";
      Runnable change = AFTER_SEARCH.remove(number);
      if (change != null) {
        change.run();
      }
      send(x, 200, "application/json", body);
    } catch (IOException | RuntimeException | InterruptedException e) {
      // the client gave up (a timeout) or sent a request this fake does not know
    } finally {
      x.close();
    }
  }

  /**
   * The 400 problem of the contract, as a short text; null when the query is valid: size 1 to 200,
   * page 0 or more, and afterNpi exactly 10 digits, given once, with page absent or 0.
   */
  private static String invalid(String raw, Map<String, String> query) {
    try {
      int size = Integer.parseInt(query.getOrDefault("size", "20"));
      int page = Integer.parseInt(query.getOrDefault("page", "0"));
      if (size < 1 || size > 200) {
        return "size must be 1 to 200";
      }
      if (page < 0) {
        return "page must be 0 or more";
      }
      if (query.containsKey("afterNpi")) {
        if (!query.get("afterNpi").matches("[0-9]{10}")) {
          return "afterNpi must be exactly 10 digits";
        }
        if (keyCount(raw) > 1) {
          return "afterNpi must be given once";
        }
        if (page != 0) {
          return "page must be absent or 0 with afterNpi";
        }
      }
      return null;
    } catch (NumberFormatException e) {
      return "page and size must be numbers";
    }
  }

  private static long keyCount(String raw) {
    return Arrays.stream(raw.split("&"))
        .map(pair -> pair.contains("=") ? pair.substring(0, pair.indexOf('=')) : pair)
        .filter(key -> URLDecoder.decode(key, StandardCharsets.UTF_8).equals("afterNpi"))
        .count();
  }

  /**
   * Applies the fault; true when the request is answered (or cut) and must not be answered again.
   */
  private static boolean fault(HttpExchange x, Fault fault) throws IOException {
    switch (fault) {
      case SERVER_ERROR -> {
        send(
            x,
            500,
            "application/problem+json",
            "{\"title\":\"Internal Server Error\",\"status\":500}");
        return true;
      }
      case CONNECTION_CUT -> {
        return true; // x.close() in the caller's finally ends the exchange without an answer
      }
      case NOT_A_PAGE -> {
        send(x, 200, "application/json", "{\"message\":\"no page here\"}");
        return true;
      }
      case NOT_JSON -> {
        send(x, 200, "text/html", "<html>maintenance</html>");
        return true;
      }
      case SLOW -> {
        try {
          Thread.sleep(SLOW_MILLIS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        return false; // then answers normally, long after the client has given up
      }
    }
    return false;
  }

  private static String json(Prov p) {
    String effective =
        p.effectiveDate() == null ? "" : ",\"effectiveDate\":\"" + p.effectiveDate() + "\"";
    return "{\"npi\":\""
        + p.npi()
        + "\",\"medicaidId\":\""
        + p.medicaidId()
        + "\",\"providerType\":\"IN\",\"taxonomy\":\"207Q00000X\",\"enrollStatus\":\""
        + p.status()
        + "\",\"screenFlag\":\"P\""
        + effective
        + ",\"version\":1}";
  }

  private static void send(HttpExchange x, int status, String contentType, String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    x.getResponseHeaders().set("Content-Type", contentType);
    x.sendResponseHeaders(status, bytes.length);
    try (OutputStream out = x.getResponseBody()) {
      out.write(bytes);
    }
  }

  private static Map<String, String> query(String raw) {
    Map<String, String> out = new LinkedHashMap<>();
    if (raw != null && !raw.isEmpty()) {
      for (String pair : raw.split("&")) {
        int eq = pair.indexOf('=');
        String key = eq < 0 ? pair : pair.substring(0, eq);
        String value = eq < 0 ? "" : pair.substring(eq + 1);
        out.put(
            URLDecoder.decode(key, StandardCharsets.UTF_8),
            URLDecoder.decode(value, StandardCharsets.UTF_8));
      }
    }
    return out;
  }
}
