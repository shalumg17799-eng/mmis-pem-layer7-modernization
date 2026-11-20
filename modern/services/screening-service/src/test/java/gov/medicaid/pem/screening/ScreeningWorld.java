package gov.medicaid.pem.screening;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static gov.medicaid.pem.screening.TestSources.MOCK;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import java.util.List;

/**
 * The mocked world of the screening tests of Lane 3 (MAM-20, MAM-22, MAM-28, MAM-32): the four
 * external sources and provider-service as stubs of the one WireMock of TestSources, with the
 * request logs to check what screening-service asked. Synthetic data only (the NPIs, licences and
 * names of aws-transform/test-data and modern/testdata).
 */
public final class ScreeningWorld {

  public static final String LEIE = "/leie/exclusions";
  public static final String SAM = "/sam/debarments";
  public static final String DMF = "/ssa-dmf/deaths";
  public static final String LICENCE = "/license-board/licenses";
  public static final String PROVIDERS = "/api/v1/providers";

  /** The four sources in the order SCRNOUT writes them. */
  public static final List<String> SOURCES = List.of("LICBOARD", "OIGLEIE", "SAMGOV", "SSADMF");

  private static final ObjectMapper JSON = new ObjectMapper();

  private ScreeningWorld() {}

  // ---- the four sources -------------------------------------------------------------------

  /** Path of a source: LICBOARD, OIGLEIE, SAMGOV or SSADMF. */
  public static String pathOf(String source) {
    return switch (source) {
      case "OIGLEIE" -> LEIE;
      case "SAMGOV" -> SAM;
      case "SSADMF" -> DMF;
      case "LICBOARD" -> LICENCE;
      default -> throw new IllegalArgumentException("no such source: " + source);
    };
  }

  /** The query parameter that identifies the lookup: the NPI, or the licence number. */
  public static String keyOf(String source) {
    return "LICBOARD".equals(source) ? "licenseNo" : "npi";
  }

  /** Every source answers "clean" for anyone and the licence board knows no licence (priority 10). */
  public static void cleanSources() {
    MOCK.stubFor(
        get(urlPathEqualTo(LEIE))
            .atPriority(10)
            .willReturn(okJson("{\"source\":\"OIGLEIE\",\"excluded\":false}")));
    MOCK.stubFor(
        get(urlPathEqualTo(SAM))
            .atPriority(10)
            .willReturn(okJson("{\"source\":\"SAMGOV\",\"debarred\":false}")));
    MOCK.stubFor(
        get(urlPathEqualTo(DMF))
            .atPriority(10)
            .willReturn(okJson("{\"source\":\"SSADMF\",\"deceased\":false}")));
    MOCK.stubFor(
        get(urlPathEqualTo(LICENCE))
            .atPriority(10)
            .willReturn(okJson("{\"source\":\"LICBOARD\",\"found\":false}")));
  }

  /** The licence board knows this licence (status ACTIVE or EXPIRED, with its expiry). */
  public static void licence(String licence, String state, String status, String expiry) {
    licence(licence, state, status, expiry, 1);
  }

  /** The same with a stub priority (1 is the strongest); a later stub of equal priority wins. */
  public static void licence(
      String licence, String state, String status, String expiry, int priority) {
    MOCK.stubFor(
        get(urlPathEqualTo(LICENCE))
            .atPriority(priority)
            .withQueryParam("licenseNo", equalTo(licence))
            .withQueryParam("state", equalTo(state))
            .willReturn(
                okJson(
                    "{\"source\":\"LICBOARD\",\"licenseNo\":\""
                        + licence
                        + "\",\"state\":\""
                        + state
                        + "\",\"found\":true,\"status\":\""
                        + status
                        + "\",\"expiry\":\""
                        + expiry
                        + "\"}")));
  }

  /** The LEIE lists this NPI (the sample hit of the WireMock stub: 1128(a)(1), 2023-01-15). */
  public static void leieListed(String npi) {
    MOCK.stubFor(
        get(urlPathEqualTo(LEIE))
            .atPriority(1)
            .withQueryParam("npi", equalTo(npi))
            .willReturn(
                okJson(
                    "{\"source\":\"OIGLEIE\",\"npi\":\""
                        + npi
                        + "\",\"excluded\":true,\"exclusionType\":\"1128(a)(1)\","
                        + "\"description\":\"PROGRAM-RELATED FRAUD\",\"exclusionDate\":\"2023-01-15\"}")));
  }

  /** The error stub of the mocks (HTTP status, default 500) for one lookup key of one source. */
  public static void sourceError(String source, String key, int status) {
    MOCK.stubFor(
        get(urlPathEqualTo(pathOf(source)))
            .atPriority(1)
            .withQueryParam(keyOf(source), equalTo(key))
            .willReturn(
                aResponse()
                    .withStatus(status)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"source\":\"" + source + "\",\"error\":\"stubbed server error\"}")));
  }

  /** A lookup that ends without any HTTP answer (the connection is reset or the reply is empty). */
  public static void sourceFault(String source, String key, Fault fault) {
    MOCK.stubFor(
        get(urlPathEqualTo(pathOf(source)))
            .atPriority(1)
            .withQueryParam(keyOf(source), equalTo(key))
            .willReturn(aResponse().withFault(fault)));
  }

  /** The slow stub: the clean answer, but only after {@code millis} milliseconds. */
  public static void sourceSlow(String source, String key, int millis) {
    MOCK.stubFor(
        get(urlPathEqualTo(pathOf(source)))
            .atPriority(1)
            .withQueryParam(keyOf(source), equalTo(key))
            .willReturn(okJson(cleanBody(source)).withFixedDelay(millis)));
  }

  /** The clean answer of a source. */
  public static String cleanBody(String source) {
    return switch (source) {
      case "OIGLEIE" -> "{\"source\":\"OIGLEIE\",\"excluded\":false}";
      case "SAMGOV" -> "{\"source\":\"SAMGOV\",\"debarred\":false}";
      case "SSADMF" -> "{\"source\":\"SSADMF\",\"deceased\":false}";
      case "LICBOARD" ->
          "{\"source\":\"LICBOARD\",\"found\":true,\"status\":\"ACTIVE\",\"expiry\":\"2030-12\"}";
      default -> throw new IllegalArgumentException("no such source: " + source);
    };
  }

  /** How many lookups of the source for this key (NPI or licence number) the mock has logged. */
  public static int lookups(String source, String key) {
    return MOCK.findAll(
            getRequestedFor(urlPathEqualTo(pathOf(source)))
                .withQueryParam(keyOf(source), equalTo(key)))
        .size();
  }

  /** How many lookups of the source, whatever the key. */
  public static int lookups(String source) {
    return MOCK.findAll(getRequestedFor(urlPathEqualTo(pathOf(source)))).size();
  }

  /** How many lookups the four sources together have logged. */
  public static int allLookups() {
    return SOURCES.stream().mapToInt(ScreeningWorld::lookups).sum();
  }

  // ---- provider-service -------------------------------------------------------------------

  /** A provider as provider-service answers it (contract Provider): screen flag U, status P. */
  public static ObjectNode base(String npi, String type, String taxonomy, String flag) {
    ObjectNode p = JSON.createObjectNode();
    p.put("npi", npi);
    p.put("medicaidId", "MCD000" + npi.substring(7));
    p.put("providerType", type);
    p.put("taxonomy", taxonomy);
    p.put("enrollStatus", "P");
    p.put("screenFlag", flag);
    p.put("effectiveDate", "2026-11-18");
    p.put("version", 0);
    return p;
  }

  /** An individual (IN) with a licence; a null licence or state leaves that member out. */
  public static ObjectNode individual(
      String npi, String first, String last, String licence, String state) {
    ObjectNode p = base(npi, "IN", "207Q00000X", "U");
    ObjectNode i = p.putObject("individual");
    i.put("lastName", last);
    i.put("firstName", first);
    if (licence != null) {
      i.put("licenseNo", licence);
    }
    if (state != null) {
      i.put("licenseState", state);
    }
    return p;
  }

  /** A group (GR) or facility (FA); a null owner NPI leaves the member out. */
  public static ObjectNode organization(
      String npi, String type, String legalName, String ownerNpi) {
    ObjectNode p = base(npi, type, "251G00000X", "U");
    ObjectNode o = p.putObject("organization");
    o.put("legalName", legalName);
    if (ownerNpi != null) {
      o.put("ownerNpi", ownerNpi);
    }
    return p;
  }

  /** The same provider with another screen flag. */
  public static ObjectNode withFlag(ObjectNode provider, String flag) {
    provider.put("screenFlag", flag);
    return provider;
  }

  /** provider-service answers GET /api/v1/providers/{npi} with this provider. */
  public static void holds(ObjectNode provider) {
    MOCK.stubFor(
        get(urlPathEqualTo(PROVIDERS + "/" + provider.path("npi").asText()))
            .atPriority(5)
            .willReturn(okJson(provider.toString())));
  }

  /** provider-service answers GET /api/v1/providers/{npi} with this status (a 5xx: it is down). */
  public static StubMapping providerAnswers(String npi, int status) {
    return MOCK.stubFor(
        get(urlPathEqualTo(PROVIDERS + "/" + npi))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(status)
                    .withHeader("Content-Type", "application/problem+json")
                    .withBody("{\"title\":\"stubbed\",\"status\":" + status + "}")));
  }

  /** provider-service does not know the NPI (404). */
  public static void providerUnknown(String npi) {
    providerAnswers(npi, 404);
  }

  /** provider-service answers every page of the list with this status; returns the stub. */
  public static StubMapping listAnswers(int status) {
    return MOCK.stubFor(
        get(urlPathEqualTo(PROVIDERS))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(status)
                    .withHeader("Content-Type", "application/problem+json")
                    .withBody("{\"title\":\"stubbed\",\"status\":" + status + "}")));
  }

  /** provider-service answers, but only after {@code millis} milliseconds. */
  public static void providerSlow(ObjectNode provider, int millis) {
    MOCK.stubFor(
        get(urlPathEqualTo(PROVIDERS + "/" + provider.path("npi").asText()))
            .atPriority(1)
            .willReturn(okJson(provider.toString()).withFixedDelay(millis)));
  }

  /** provider-service ends the call without an answer. */
  public static void providerFault(String npi, Fault fault) {
    MOCK.stubFor(
        get(urlPathEqualTo(PROVIDERS + "/" + npi))
            .atPriority(1)
            .willReturn(aResponse().withFault(fault)));
  }

  /** How often provider-service was asked for this one provider. */
  public static int providerReads(String npi) {
    return MOCK.findAll(getRequestedFor(urlPathEqualTo(PROVIDERS + "/" + npi))).size();
  }

  /**
   * provider-service answers the list GET /api/v1/providers?page=n&size=200 page by page with
   * these providers, and each of them also by NPI.
   */
  public static void lists(List<ObjectNode> providers) {
    int size = 200;
    int pages = Math.max(1, (providers.size() + size - 1) / size);
    for (ObjectNode p : providers) {
      holds(p);
    }
    for (int page = 0; page < pages; page++) {
      ArrayNode items = JSON.createArrayNode();
      providers.subList(page * size, Math.min(providers.size(), (page + 1) * size))
          .forEach(p -> items.add(p));
      ObjectNode body = JSON.createObjectNode();
      body.set("items", items);
      body.put("page", page);
      body.put("size", size);
      body.put("total", providers.size());
      MOCK.stubFor(
          get(urlPathEqualTo(PROVIDERS))
              .atPriority(2)
              .withQueryParam("page", equalTo(String.valueOf(page)))
              .withQueryParam("size", equalTo(String.valueOf(size)))
              .willReturn(okJson(body.toString())));
    }
  }

  /** The pages of the list the mock was asked for, in the order of the requests. */
  public static List<String> listedPages() {
    return MOCK.findAll(getRequestedFor(urlPathEqualTo(PROVIDERS))).stream()
        .map(r -> r.queryParameter("page").firstValue())
        .toList();
  }
}
