package gov.medicaid.pem.apigateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.yaml.snakeyaml.Yaml;

/**
 * MAM-29 (plt-gateway): every contract path reaches its owning service through the gateway, with
 * the bearer token unchanged and the correlation id; CORS only for the web UI; problem details for
 * unknown paths and stopped services. Each service is a tiny stub HTTP server (JDK) that records
 * what it received.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.config.name=api-gateway", "PEM_LOG_FORMAT="})
class GatewayRoutingTest {

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
  static final Map<String, HttpServer> STUBS = new LinkedHashMap<>();
  static final Map<String, List<Map<String, String>>> SEEN = new ConcurrentHashMap<>();
  static int stoppedPort;

  static {
    for (String s : SERVICES) {
      if (s.equals("tmsis")) {
        continue; // tmsis is "stopped": nothing listens on its port
      }
      try {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
            "/",
            ex -> {
              var h = new java.util.HashMap<String, String>();
              h.put("path", ex.getRequestURI().toString());
              h.put("method", ex.getRequestMethod());
              h.put(
                  "authorization",
                  String.valueOf(ex.getRequestHeaders().getFirst("Authorization")));
              h.put(
                  "correlation",
                  String.valueOf(ex.getRequestHeaders().getFirst("X-Correlation-Id")));
              SEEN.computeIfAbsent(s, k -> new ArrayList<>()).add(h);
              byte[] body = ("{\"service\":\"" + s + "\"}").getBytes(StandardCharsets.UTF_8);
              ex.getResponseHeaders().add("Content-Type", "application/json");
              ex.sendResponseHeaders(200, body.length);
              ex.getResponseBody().write(body);
              ex.close();
            });
        server.start();
        STUBS.put(s, server);
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
    try (var socket = new java.net.ServerSocket(0)) {
      stoppedPort = socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void serviceUrls(DynamicPropertyRegistry r) {
    for (String s : SERVICES) {
      int port = STUBS.containsKey(s) ? STUBS.get(s).getAddress().getPort() : stoppedPort;
      r.add("pem.gateway.services." + s, () -> "http://127.0.0.1:" + port);
    }
  }

  @AfterAll
  static void stop() {
    STUBS.values().forEach(s -> s.stop(0));
  }

  @LocalServerPort int port;
  final HttpClient http = HttpClient.newHttpClient();

  HttpResponse<String> call(String method, String path, Map<String, String> headers)
      throws Exception {
    var b =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .method(
                method,
                method.equals("POST") || method.equals("PUT")
                    ? HttpRequest.BodyPublishers.ofString("{}")
                    : HttpRequest.BodyPublishers.noBody());
    headers.forEach(b::header);
    if (method.equals("POST") || method.equals("PUT")) {
      b.header("Content-Type", "application/json");
    }
    return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
  }

  /**
   * Every operation of every contract, with sample path values, and the service that owns it (file
   * name).
   */
  static Stream<Arguments> contractOperations() throws IOException {
    Path dir = locate(Path.of("contracts", "openapi"));
    List<Arguments> out = new ArrayList<>();
    try (var files = Files.list(dir)) {
      for (Path f : files.sorted().toList()) {
        String svc = f.getFileName().toString().replace("-service.yaml", "");
        Map<String, Object> spec = new Yaml().load(Files.readString(f));
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> paths =
            (Map<String, Map<String, Object>>) spec.get("paths");
        for (var p : paths.entrySet()) {
          for (String method : p.getValue().keySet()) {
            String sample =
                p.getKey()
                    .replaceAll("\\{npi}", "1003000111")
                    .replaceAll("\\{[A-Za-z]+}", "00000000-0000-0000-0000-000000000001");
            out.add(Arguments.of(method.toUpperCase(), sample, svc));
          }
        }
      }
    }
    return out.stream();
  }

  @ParameterizedTest(name = "{0} {1} -> {2}-service")
  @MethodSource("contractOperations")
  @DisplayName("[MAM-29 AC-1] every contract path is routed to its owning service")
  void routesEveryContractPath(String method, String path, String service) throws Exception {
    if (service.equals("tmsis")) {
      // the tmsis stub is stopped in this test: the route exists and answers 503 (AC-6)
      assertThat(call(method, path, Map.of()).statusCode()).isEqualTo(503);
      return;
    }
    int before = SEEN.getOrDefault(service, List.of()).size();
    var res = call(method, path, Map.of("Authorization", "Bearer abc"));
    assertThat(res.statusCode()).isEqualTo(200);
    assertThat(res.body()).contains("\"service\":\"" + service + "\"");
    var last = SEEN.get(service).get(SEEN.get(service).size() - 1);
    assertThat(SEEN.get(service)).hasSize(before + 1);
    assertThat(last.get("path")).isEqualTo(path);
  }

  @Test
  @DisplayName(
      "[MAM-29 AC-3] the bearer token reaches the service unchanged; the gateway adds no identity header")
  void relaysTheToken() throws Exception {
    call("GET", "/api/v1/providers/1003000111", Map.of("Authorization", "Bearer token-xyz"));
    var last = SEEN.get("provider").get(SEEN.get("provider").size() - 1);
    assertThat(last.get("authorization")).isEqualTo("Bearer token-xyz");
  }

  @Test
  @DisplayName("[MAM-29 AC-2] directory and FHIR calls need no token and are forwarded")
  void publicCallsForwarded() throws Exception {
    assertThat(call("GET", "/api/v1/directory/providers", Map.of()).statusCode()).isEqualTo(200);
    assertThat(call("GET", "/fhir/Practitioner", Map.of()).statusCode()).isEqualTo(200);
  }

  @Test
  @DisplayName("[MAM-29 AC-5] the correlation id is kept or generated, forwarded, and returned")
  void correlationId() throws Exception {
    var kept = call("GET", "/api/v1/providers/1003000111", Map.of("X-Correlation-Id", "gw-1"));
    assertThat(kept.headers().firstValue("X-Correlation-Id")).contains("gw-1");
    assertThat(SEEN.get("provider").get(SEEN.get("provider").size() - 1).get("correlation"))
        .isEqualTo("gw-1");
    var generated = call("GET", "/api/v1/providers/1003000111", Map.of());
    String id = generated.headers().firstValue("X-Correlation-Id").orElseThrow();
    assertThat(id).isNotBlank();
    assertThat(SEEN.get("provider").get(SEEN.get("provider").size() - 1).get("correlation"))
        .isEqualTo(id);
  }

  @Test
  @DisplayName(
      "[MAM-29 AC-4] CORS allows http://localhost:5173 only, with Authorization, Content-Type and X-Correlation-Id")
  void cors() throws Exception {
    var ok =
        call(
            "OPTIONS",
            "/api/v1/providers/1003000111",
            Map.of(
                "Origin", "http://localhost:5173",
                "Access-Control-Request-Method", "GET",
                "Access-Control-Request-Headers", "authorization,content-type,x-correlation-id"));
    assertThat(ok.headers().firstValue("Access-Control-Allow-Origin"))
        .contains("http://localhost:5173");
    assertThat(ok.headers().firstValue("Access-Control-Allow-Headers").orElse("").toLowerCase())
        .contains("authorization")
        .contains("content-type")
        .contains("x-correlation-id");
    var other =
        call(
            "OPTIONS",
            "/api/v1/providers/1003000111",
            Map.of("Origin", "http://evil.example", "Access-Control-Request-Method", "GET"));
    assertThat(other.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-29 AC-6] an unknown path answers 404 problem details with the correlation id, no stack trace")
  void unknownPath() throws Exception {
    var res = call("GET", "/api/v1/nothing-here", Map.of("X-Correlation-Id", "gw-404"));
    assertThat(res.statusCode()).isEqualTo(404);
    assertThat(res.headers().firstValue("Content-Type").orElse(""))
        .contains("application/problem+json");
    assertThat(res.body())
        .contains("\"status\":404")
        .contains("gw-404")
        .doesNotContain("Exception")
        .doesNotContain("at org.");
  }

  @Test
  @DisplayName(
      "[MAM-29 AC-6] a stopped service answers 503 problem details without internal host names")
  void stoppedService() throws Exception {
    var res = call("POST", "/api/v1/batch/tmsis-extracts", Map.of("X-Correlation-Id", "gw-503"));
    assertThat(res.statusCode()).isEqualTo(503);
    assertThat(res.headers().firstValue("Content-Type").orElse(""))
        .contains("application/problem+json");
    assertThat(res.body())
        .contains("\"status\":503")
        .contains("gw-503")
        .doesNotContain("127.0.0.1")
        .doesNotContain(String.valueOf(stoppedPort))
        .doesNotContain("Exception");
  }

  private static Path locate(Path rel) {
    Path dir = Path.of("").toAbsolutePath();
    while (dir != null) {
      for (Path c : List.of(dir.resolve(rel), dir.resolve("modern").resolve(rel))) {
        if (Files.isDirectory(c)) {
          return c;
        }
      }
      dir = dir.getParent();
    }
    throw new IllegalStateException("not found: " + rel);
  }
}
