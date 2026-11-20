package gov.medicaid.pem.directory;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * One WireMock for the whole test JVM: it plays provider-service (GET /api/v1/providers/{npi}) and
 * the Keycloak token endpoint that gives directory-service its SYSTEM token.
 */
public final class TestSources {

  public static final String SYSTEM_TOKEN = "test-system-token";
  public static final WireMockServer MOCK = new WireMockServer(options().dynamicPort());

  static {
    MOCK.start();
    stubToken();
  }

  private TestSources() {}

  public static void stubToken() {
    MOCK.stubFor(
        post(urlPathEqualTo("/token"))
            .willReturn(
                okJson(
                    "{\"access_token\":\""
                        + SYSTEM_TOKEN
                        + "\",\"token_type\":\"Bearer\",\"expires_in\":300}")));
  }

  public static void register(DynamicPropertyRegistry r) {
    r.add("pem.services.provider-url", MOCK::baseUrl);
    r.add("pem.client.token-uri", () -> MOCK.baseUrl() + "/token");
    r.add("pem.client.client-secret", () -> "test-secret");
  }
}
