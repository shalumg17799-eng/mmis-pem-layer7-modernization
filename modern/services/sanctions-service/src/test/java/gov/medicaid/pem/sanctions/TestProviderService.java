package gov.medicaid.pem.sanctions;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.util.List;
import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * One WireMock for the whole test JVM. It plays provider-service as its contract describes it (GET
 * /api/v1/providers/{npi}, POST /api/v1/providers/{npi}/status-changes: TERMINATE gives T, SUSPEND
 * S, REINSTATE A, whatever the current status) and the Keycloak token endpoint for the SYSTEM
 * token. The status of each NPI is a WireMock scenario state, so a later GET shows the status the
 * last status change set.
 */
public final class TestProviderService {

  public static final WireMockServer MOCK = new WireMockServer(options().dynamicPort());
  public static final String SYSTEM_TOKEN = "test-system-token";

  private static final Map<String, String> RESULT =
      Map.of("TERMINATE", "T", "SUSPEND", "S", "REINSTATE", "A");

  static {
    MOCK.start();
    reset();
  }

  private TestProviderService() {}

  /** Forgets every provider, request and state; keeps the token endpoint. */
  public static void reset() {
    MOCK.resetAll();
    MOCK.stubFor(
        post(urlPathEqualTo("/token"))
            .willReturn(
                okJson(
                    "{\"access_token\":\""
                        + SYSTEM_TOKEN
                        + "\",\"token_type\":\"Bearer\",\"expires_in\":300}")));
  }

  static String provider(String npi, String status) {
    return "{\"npi\":\""
        + npi
        + "\",\"medicaidId\":\"MCD"
        + npi.substring(4)
        + "\",\"providerType\":\"IN\",\"taxonomy\":\"207Q00000X\",\"enrollStatus\":\""
        + status
        + "\",\"screenFlag\":\"P\",\"effectiveDate\":\"2026-01-15\",\"individual\":{\"lastName\":\"RIVERA\",\"firstName\":\"MARIA\"},\"version\":1}";
  }

  /** A provider with this status; its status changes as provider-service would apply them. */
  public static void holds(String npi, String status) {
    String scenario = "provider-" + npi;
    String path = "/api/v1/providers/" + npi;
    MOCK.stubFor(
        get(urlPathEqualTo(path))
            .inScenario(scenario)
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(okJson(provider(npi, status))));
    for (String s : List.of("P", "A", "T", "D", "S")) {
      MOCK.stubFor(
          get(urlPathEqualTo(path))
              .inScenario(scenario)
              .whenScenarioStateIs("status-" + s)
              .willReturn(okJson(provider(npi, s))));
    }
    RESULT.forEach(
        (action, result) ->
            MOCK.stubFor(
                post(urlPathEqualTo(path + "/status-changes"))
                    .withRequestBody(matchingJsonPath("$.action", equalTo(action)))
                    .inScenario(scenario)
                    .willSetStateTo("status-" + result)
                    .willReturn(okJson(provider(npi, result)))));
  }

  /** provider-service answers 404 problem+json for this NPI. */
  public static void unknown(String npi) {
    String problem =
        "{\"title\":\"Not Found\",\"status\":404,\"detail\":\"No provider found for NPI "
            + npi
            + "\"}";
    for (var m :
        List.of(
            get(urlPathEqualTo("/api/v1/providers/" + npi)),
            post(urlPathEqualTo("/api/v1/providers/" + npi + "/status-changes")))) {
      MOCK.stubFor(
          m.atPriority(1)
              .willReturn(
                  aResponse()
                      .withStatus(404)
                      .withHeader("Content-Type", "application/problem+json")
                      .withBody(problem)));
    }
  }

  /** The status change of this NPI fails: a server error, or no answer at all. */
  public static void fails(String npi, String fault) {
    var answer =
        switch (fault) {
          case "server error" ->
              aResponse()
                  .withStatus(500)
                  .withHeader("Content-Type", "application/problem+json")
                  .withBody("{\"title\":\"Internal Server Error\",\"status\":500}");
          case "unreachable" -> aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER);
          default -> throw new IllegalArgumentException("unknown fault " + fault);
        };
    MOCK.stubFor(
        post(urlPathEqualTo("/api/v1/providers/" + npi + "/status-changes"))
            .atPriority(1)
            .willReturn(answer));
  }

  public static void register(DynamicPropertyRegistry r) {
    r.add("pem.services.provider-url", MOCK::baseUrl);
    r.add("pem.client.token-uri", () -> MOCK.baseUrl() + "/token");
    r.add("pem.client.client-secret", () -> "test-secret");
  }
}
