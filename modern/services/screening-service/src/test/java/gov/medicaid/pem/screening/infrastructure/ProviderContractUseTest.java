package gov.medicaid.pem.screening.infrastructure;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static gov.medicaid.pem.screening.TestSources.MOCK;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import gov.medicaid.pem.screening.Lane3Test;
import gov.medicaid.pem.screening.ScreeningWorld;
import gov.medicaid.pem.screening.application.ProviderListing;
import gov.medicaid.pem.testsupport.OpenApiContract;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.yaml.snakeyaml.Yaml;

/**
 * The calls screening-service makes to provider-service against modern/contracts/openapi/
 * provider-service.yaml (1.2.0, which added the optional afterNpi to searchProviders; a call
 * without it answers as in 1.1.0): the request of the batch run uses only parameters the contract
 * declares, with a page and a size the contract allows and without afterNpi; SYSTEM may call both
 * operations; and the provider documents the tests stub for provider-service (an individual, a
 * group with an owner, a facility without one, a page of the list) are valid answers of the
 * contract, so the fixtures of the whole suite cannot drift away from it.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ProviderContractUseTest extends Lane3Test {

  static final String NPI = "1003000111";
  static final Map<String, Object> SPEC = load();

  @Autowired ProviderServiceClient client;

  @SuppressWarnings("unchecked")
  static Map<String, Object> load() {
    Path rel = Path.of("contracts", "openapi", "provider-service.yaml");
    Path dir = Path.of("").toAbsolutePath();
    while (dir != null) {
      for (Path c : List.of(dir.resolve(rel), dir.resolve("modern").resolve(rel))) {
        if (Files.isRegularFile(c)) {
          try {
            return new Yaml().load(Files.readString(c));
          } catch (IOException e) {
            throw new IllegalStateException("cannot read " + c, e);
          }
        }
      }
      dir = dir.getParent();
    }
    throw new IllegalStateException("contract not found: " + rel);
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> operation(String operationId) {
    for (Object item : ((Map<String, Object>) SPEC.get("paths")).values()) {
      if (item instanceof Map<?, ?> path) {
        for (Object op : path.values()) {
          if (op instanceof Map<?, ?> m && operationId.equals(m.get("operationId"))) {
            return (Map<String, Object>) m;
          }
        }
      }
    }
    throw new AssertionError("operation " + operationId + " is not in the contract");
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> parameter(String operationId, String name) {
    for (Object p : (List<Object>) operation(operationId).get("parameters")) {
      Map<String, Object> m = (Map<String, Object>) p;
      if (name.equals(m.get("name"))) {
        return m;
      }
    }
    throw new AssertionError("parameter " + name + " is not declared by " + operationId);
  }

  @SuppressWarnings("unchecked")
  static Set<String> queryParameters(String operationId) {
    Set<String> names = new LinkedHashSet<>();
    for (Object p : (List<Object>) operation(operationId).get("parameters")) {
      Map<String, Object> m = (Map<String, Object>) p;
      if ("query".equals(m.get("in"))) {
        names.add((String) m.get("name"));
      }
    }
    return names;
  }

  static Set<String> queryNamesOf(String url) {
    Set<String> names = new LinkedHashSet<>();
    int q = url.indexOf('?');
    if (q >= 0) {
      for (String pair : url.substring(q + 1).split("&")) {
        names.add(pair.split("=", 2)[0]);
      }
    }
    return names;
  }

  /** The body the mock answers for the path, fetched over HTTP like any client does. */
  static String fetch(String pathAndQuery) throws Exception {
    HttpResponse<String> r =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create(MOCK.baseUrl() + pathAndQuery))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    assertThat(r.statusCode()).isEqualTo(200);
    return r.body();
  }

  // ---- the requests -----------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-32 AC-2] the batch run asks the provider list with page and size only: both are declared by searchProviders and it sends no afterNpi")
  void listRequestUsesDeclaredParameters() {
    ScreeningWorld.lists(
        List.of(
            ScreeningWorld.withFlag(
                ScreeningWorld.individual(NPI, "MARIA", "RIVERA", "MD12345", "TX"), "U")));
    client.page(0, ProviderListing.PAGE_SIZE);
    List<LoggedRequest> asked =
        MOCK.findAll(getRequestedFor(urlPathEqualTo(ScreeningWorld.PROVIDERS)));
    assertThat(asked).hasSize(1);
    Set<String> sent = queryNamesOf(asked.get(0).getUrl());
    assertThat(sent).containsExactlyInAnyOrder("page", "size");
    assertThat(queryParameters("searchProviders")).containsAll(sent);
    assertThat(sent).doesNotContain("afterNpi");
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-2] the page size of the batch run is within the contract's size range and page 0 is a legal page")
  void pageSizeIsWithinTheContractRange() {
    @SuppressWarnings("unchecked")
    Map<String, Object> size =
        (Map<String, Object>) parameter("searchProviders", "size").get("schema");
    @SuppressWarnings("unchecked")
    Map<String, Object> page =
        (Map<String, Object>) parameter("searchProviders", "page").get("schema");
    assertThat(((Number) size.get("minimum")).intValue())
        .isLessThanOrEqualTo(ProviderListing.PAGE_SIZE);
    assertThat(((Number) size.get("maximum")).intValue())
        .as("the page size of the batch run must not exceed the contract's maximum")
        .isGreaterThanOrEqualTo(ProviderListing.PAGE_SIZE);
    assertThat(((Number) page.get("minimum")).intValue()).isZero();
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-5] the provider is read with GET /api/v1/providers/{npi}, an operation of the contract with a 10-digit NPI")
  void providerReadIsAnOperationOfTheContract() {
    ScreeningWorld.holds(ScreeningWorld.individual(NPI, "MARIA", "RIVERA", "MD12345", "TX"));
    client.facts(NPI);
    @SuppressWarnings("unchecked")
    Map<String, Object> paths = (Map<String, Object>) SPEC.get("paths");
    assertThat(paths).containsKey("/api/v1/providers/{npi}");
    assertThat(operation("getProvider").get("operationId")).isEqualTo("getProvider");
    List<LoggedRequest> asked =
        MOCK.findAll(getRequestedFor(urlPathEqualTo(ScreeningWorld.PROVIDERS + "/" + NPI)));
    assertThat(asked).hasSize(1);
    assertThat(asked.get(0).getUrl()).matches("^/api/v1/providers/[0-9]{10}$");
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-2] the SYSTEM role, the token screening-service calls with, may use searchProviders and getProvider")
  void systemMayReadProviders() {
    assertThat(operationRoles("searchProviders")).contains("SYSTEM");
    assertThat(operationRoles("getProvider")).contains("SYSTEM");
  }

  @SuppressWarnings("unchecked")
  static List<String> operationRoles(String operationId) {
    return (List<String>) operation(operationId).get("x-roles");
  }

  // ---- the stubbed answers ----------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-28 AC-5] an individual as the tests stub it is a valid getProvider answer of the provider contract")
  void individualFixtureIsValid() throws Exception {
    ScreeningWorld.holds(ScreeningWorld.individual(NPI, "MARIA", "RIVERA", "MD12345", "TX"));
    OpenApiContract.of("provider-service")
        .assertResponse("getProvider", 200, fetch(ScreeningWorld.PROVIDERS + "/" + NPI));
  }

  @Test
  @DisplayName(
      "[MAM-22 AC-3] a group with an owner and a facility without one, as the tests stub them, are valid getProvider answers")
  void organisationFixturesAreValid() throws Exception {
    ObjectNode group =
        ScreeningWorld.organization("1003000444", "GR", "EXCLUDED CLINIC LLC", "1990000111");
    ObjectNode facility =
        ScreeningWorld.organization("1003000333", "FA", "SUNSET HOSPICE INC", null);
    ScreeningWorld.holds(group);
    ScreeningWorld.holds(facility);
    OpenApiContract contract = OpenApiContract.of("provider-service");
    contract.assertResponse("getProvider", 200, fetch(ScreeningWorld.PROVIDERS + "/1003000444"));
    contract.assertResponse("getProvider", 200, fetch(ScreeningWorld.PROVIDERS + "/1003000333"));
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-2] a page of the provider list as the tests stub it is a valid searchProviders answer of the provider contract")
  void listFixtureIsValid() throws Exception {
    ScreeningWorld.lists(
        List.of(
            ScreeningWorld.withFlag(
                ScreeningWorld.individual(NPI, "MARIA", "RIVERA", "MD12345", "TX"), "U"),
            ScreeningWorld.withFlag(
                ScreeningWorld.organization(
                    "1003000222", "GR", "NORTHSIDE CLINIC GR", "1990000999"),
                "P")));
    OpenApiContract.of("provider-service")
        .assertResponse(
            "searchProviders",
            200,
            fetch(ScreeningWorld.PROVIDERS + "?page=0&size=" + ProviderListing.PAGE_SIZE));
  }
}
