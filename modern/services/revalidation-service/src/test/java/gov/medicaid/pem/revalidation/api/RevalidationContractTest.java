package gov.medicaid.pem.revalidation.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.revalidation.ContractFile;
import gov.medicaid.pem.revalidation.IntegrationTest;
import gov.medicaid.pem.revalidation.RevalidationFixtures;
import gov.medicaid.pem.testsupport.PemJwt;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

/**
 * The contract of revalidation-service (modern/contracts/openapi/revalidation-service.yaml 1.1.0)
 * against the real application: the operations served are exactly the operations of the contract; a
 * role may call an operation exactly when the contract's x-roles lists it (403 otherwise, 401 with
 * no token); 401 and 403 are application/problem+json that conform to the contract's Problem; and
 * each documented answer has the documented shape.
 */
@Timeout(180)
class RevalidationContractTest extends IntegrationTest {

  static final String CONTRACT = "revalidation-service";
  static final List<String> ROLES = List.of("ANALYST", "SUPERVISOR", "SYSTEM", "ADMIN", "PROVIDER");

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping mappings;

  @BeforeEach
  void cleanBefore() {
    RevalidationFixtures.reset(jdbc);
  }

  @AfterEach
  void cleanAfter() {
    RevalidationFixtures.reset(jdbc);
  }

  /** One operation of the contract: its id, method, path with {placeholders} and x-roles. */
  record Operation(String id, String method, String path, List<String> roles) {

    @Override
    public String toString() {
      return method + " " + path + " (" + id + ")";
    }
  }

  @SuppressWarnings("unchecked")
  static List<Operation> operations() {
    Path rel = Path.of("contracts", "openapi", CONTRACT + ".yaml");
    Path dir = Path.of("").toAbsolutePath();
    Path found = null;
    while (dir != null && found == null) {
      for (Path c : List.of(dir.resolve(rel), dir.resolve("modern").resolve(rel))) {
        if (Files.isRegularFile(c)) {
          found = c;
        }
      }
      dir = dir.getParent();
    }
    assertThat(found).as("contract file").isNotNull();
    Map<String, Object> spec;
    try {
      spec = new Yaml().load(Files.readString(found));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    List<Operation> out = new ArrayList<>();
    Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
    paths.forEach(
        (path, item) ->
            ((Map<String, Object>) item)
                .forEach(
                    (method, op) -> {
                      Map<String, Object> o = (Map<String, Object>) op;
                      out.add(
                          new Operation(
                              (String) o.get("operationId"),
                              method.toUpperCase(),
                              path,
                              (List<String>) o.get("x-roles")));
                    }));
    return out;
  }

  static Stream<Arguments> operationsAndRoles() {
    List<Arguments> args = new ArrayList<>();
    for (Operation op : operations()) {
      for (String role : ROLES) {
        args.add(Arguments.of(op, role));
      }
    }
    return args.stream();
  }

  static Stream<Operation> everyOperation() {
    return operations().stream();
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor token(
      String role) {
    return switch (role) {
      case "ANALYST" -> PemJwt.analyst();
      case "SUPERVISOR" -> PemJwt.supervisor();
      case "SYSTEM" -> PemJwt.system();
      case "ADMIN" -> PemJwt.admin();
      case "PROVIDER" -> PemJwt.provider("1003000111");
      default -> throw new IllegalArgumentException(role);
    };
  }

  /**
   * A request that does nothing when it is allowed: path ids are random UUIDs (404) and a POST body
   * is {} (400), so no run is started and no case changes.
   */
  private MockHttpServletRequestBuilder harmless(Operation op) {
    String path = op.path().replaceAll("\\{[a-zA-Z]+\\}", UUID.randomUUID().toString());
    MockHttpServletRequestBuilder request =
        MockMvcRequestBuilders.request(
            org.springframework.http.HttpMethod.valueOf(op.method()), path);
    if (op.method().equals("POST")) {
      request.contentType(MediaType.APPLICATION_JSON).content("{}");
    }
    return request;
  }

  // ---- the operations served are the operations of the contract --------------------------------

  @Test
  @DisplayName(
      "[MAM-45 AC-1] the application serves exactly the operations of the contract under /api/v1")
  void servedOperationsAreTheContractOperations() {
    Set<String> served = new TreeSet<>();
    for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
      for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
        if (pattern.startsWith("/api/v1")) {
          info.getMethodsCondition()
              .getMethods()
              .forEach(m -> served.add(m.name() + " " + pattern));
        }
      }
    }
    Set<String> contract = new TreeSet<>();
    operations().forEach(op -> contract.add(op.method() + " " + op.path()));

    assertThat(served).isEqualTo(contract);
  }

  @Test
  @DisplayName("[MAM-48 AC-8] the contract lists the roles of its five operations")
  void everyOperationHasRoles() {
    assertThat(operations()).hasSize(5).allSatisfy(op -> assertThat(op.roles()).isNotEmpty());
  }

  // ---- roles: x-roles of the contract ----------------------------------------------------------

  @ParameterizedTest(name = "[MAM-48 AC-8] {0} with the role {1}")
  @MethodSource("operationsAndRoles")
  void rolesFollowTheContract(Operation op, String role) throws Exception {
    MvcResult r = mvc.perform(harmless(op).with(token(role))).andReturn();

    if (op.roles().contains(role)) {
      assertThat(r.getResponse().getStatus()).as("%s may call %s", role, op).isNotIn(401, 403);
    } else {
      assertThat(r.getResponse().getStatus()).as("%s may not call %s", role, op).isEqualTo(403);
    }
  }

  @ParameterizedTest(name = "[MAM-48 AC-8] {0} with no token is 401")
  @MethodSource("everyOperation")
  void noTokenIs401(Operation op) throws Exception {
    MvcResult r = mvc.perform(harmless(op)).andReturn();

    assertThat(r.getResponse().getStatus()).isEqualTo(401);
  }

  // ---- 401 and 403 are application/problem+json of the contract's Problem schema ----------------

  @ParameterizedTest(
      name = "[MAM-48 AC-8] the 401 of {0} is a problem+json that conforms to the contract")
  @MethodSource("everyOperation")
  void problemOf401(Operation op) throws Exception {
    MvcResult r = mvc.perform(harmless(op)).andReturn();

    assertThat(r.getResponse().getStatus()).isEqualTo(401);
    assertThat(r.getResponse().getContentType()).contains("application/problem+json");
    JsonNode p = RevalidationFixtures.read(r.getResponse().getContentAsString());
    assertThat(p.path("title").asText()).isNotBlank();
    assertThat(p.path("status").asInt()).isEqualTo(401);
    conforms(op.id(), 401, r);
  }

  @ParameterizedTest(
      name = "[MAM-48 AC-8] the 403 of {0} is a problem+json that conforms to the contract")
  @MethodSource("everyOperation")
  void problemOf403(Operation op) throws Exception {
    String forbidden =
        ROLES.stream().filter(role -> !op.roles().contains(role)).findFirst().orElseThrow();

    MvcResult r = mvc.perform(harmless(op).with(token(forbidden))).andReturn();

    assertThat(r.getResponse().getStatus()).isEqualTo(403);
    assertThat(r.getResponse().getContentType()).contains("application/problem+json");
    JsonNode p = RevalidationFixtures.read(r.getResponse().getContentAsString());
    assertThat(p.path("title").asText()).isNotBlank();
    assertThat(p.path("status").asInt()).isEqualTo(403);
    conforms(op.id(), 403, r);
  }

  // ---- the documented answers have the documented shape ----------------------------------------

  private static void conforms(String operation, int status, MvcResult r) throws Exception {
    String text = r.getResponse().getContentAsString();
    assertThat(ContractFile.responseViolations(operation, status, RevalidationFixtures.read(text)))
        .as("%s %s: %s", operation, status, text)
        .isEmpty();
  }

  private MvcResult as(MockHttpServletRequestBuilder request, String role) throws Exception {
    return mvc.perform(request.with(token(role))).andReturn();
  }

  @Test
  @DisplayName("[MAM-48 AC-1] listRevalidationCases: 200 is a RevalidationCasePage, 400 a Problem")
  void listContract() throws Exception {
    RevalidationFixtures.insertCase(jdbc, "1003000222", LocalDate.of(2026, 2, 28), "DUE");
    RevalidationFixtures.insertCase(jdbc, "1003000333", LocalDate.of(2026, 2, 28), "COMPLETED");

    MvcResult ok = as(MockMvcRequestBuilders.get("/api/v1/revalidations"), "ANALYST");
    MvcResult bad = as(MockMvcRequestBuilders.get("/api/v1/revalidations?status=OPEN"), "ANALYST");

    assertThat(ok.getResponse().getStatus()).isEqualTo(200);
    conforms("listRevalidationCases", 200, ok);
    assertThat(bad.getResponse().getStatus()).isEqualTo(400);
    conforms("listRevalidationCases", 400, bad);
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-2] completeRevalidation: 200 is a RevalidationCase, 404 and 409 are Problems")
  void completionContract() throws Exception {
    UUID open =
        RevalidationFixtures.insertCase(jdbc, "1003000222", LocalDate.of(2026, 2, 28), "DUE");
    String body = "{\"screeningId\":\"" + UUID.randomUUID() + "\",\"result\":\"PASS\"}";
    String path = "/api/v1/revalidations/{caseId}/completion";

    MvcResult ok =
        as(
            MockMvcRequestBuilders.post(path, open)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
            "ANALYST");
    MvcResult conflict =
        as(
            MockMvcRequestBuilders.post(path, open)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
            "SYSTEM");
    MvcResult missing =
        as(
            MockMvcRequestBuilders.post(path, UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
            "ANALYST");

    assertThat(ok.getResponse().getStatus()).isEqualTo(200);
    conforms("completeRevalidation", 200, ok);
    assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
    conforms("completeRevalidation", 409, conflict);
    assertThat(missing.getResponse().getStatus()).isEqualTo(404);
    conforms("completeRevalidation", 404, missing);
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-1] startRevalidationSweep: 202 is a BatchRun, 400 a Problem; getRevalidationSweep: 200 and 404")
  void sweepContract() throws Exception {

    MvcResult started =
        RevalidationFixtures.startSweep(mvc, PemJwt.admin(), "{\"businessDate\":\"2026-03-31\"}");
    MvcResult refused = RevalidationFixtures.startSweep(mvc, PemJwt.admin(), "{}");
    UUID run =
        UUID.fromString(
            RevalidationFixtures.read(started.getResponse().getContentAsString())
                .path("runId")
                .asText());
    RevalidationFixtures.awaitRun(mvc, run);
    MvcResult read =
        as(MockMvcRequestBuilders.get(RevalidationFixtures.SWEEPS + "/{runId}", run), "ADMIN");
    MvcResult unknown =
        as(
            MockMvcRequestBuilders.get(RevalidationFixtures.SWEEPS + "/{runId}", UUID.randomUUID()),
            "ADMIN");

    assertThat(started.getResponse().getStatus()).isEqualTo(202);
    conforms("startRevalidationSweep", 202, started);
    assertThat(refused.getResponse().getStatus()).isEqualTo(400);
    conforms("startRevalidationSweep", 400, refused);
    assertThat(read.getResponse().getStatus()).isEqualTo(200);
    conforms("getRevalidationSweep", 200, read);
    assertThat(unknown.getResponse().getStatus()).isEqualTo(404);
    conforms("getRevalidationSweep", 404, unknown);
  }

  @Test
  @DisplayName("[MAM-45 AC-7] getRevalidationQueueFile: 200 is text/plain, 404 a Problem")
  void queueFileContract() throws Exception {
    UUID run =
        RevalidationFixtures.insertRun(
            jdbc, "COMPLETED", LocalDate.of(2026, 3, 31), 0, 0, "", null);
    MvcResult ok =
        as(
            MockMvcRequestBuilders.get(RevalidationFixtures.SWEEPS + "/{runId}/queue-file", run),
            "ADMIN");
    MvcResult unknown =
        as(
            MockMvcRequestBuilders.get(
                RevalidationFixtures.SWEEPS + "/{runId}/queue-file", UUID.randomUUID()),
            "ADMIN");

    assertThat(ok.getResponse().getStatus()).isEqualTo(200);
    assertThat(ok.getResponse().getContentType()).startsWith("text/plain");
    assertThat(unknown.getResponse().getStatus()).isEqualTo(404);
    conforms("getRevalidationQueueFile", 404, unknown);
  }

  /** Documented here, not changed: the request bodies the contract does not list a 400 for. */
  @Test
  @DisplayName(
      "[MAM-48 AC-2] the contract of completeRevalidation lists no 400, but the service answers 400 with a problem")
  void completionBadRequestIsAProblem() throws Exception {
    UUID open =
        RevalidationFixtures.insertCase(jdbc, "1003000222", LocalDate.of(2026, 2, 28), "DUE");

    MvcResult bad =
        as(
            MockMvcRequestBuilders.post("/api/v1/revalidations/{caseId}/completion", open)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
            "ANALYST");

    assertThat(bad.getResponse().getStatus()).isEqualTo(400);
    assertThat(bad.getResponse().getContentType()).contains("application/problem+json");
    JsonNode problem = RevalidationFixtures.read(bad.getResponse().getContentAsString());
    assertThat(problem.path("title").asText()).isNotBlank();
    assertThat(problem.path("status").asInt()).isEqualTo(400);
  }
}
