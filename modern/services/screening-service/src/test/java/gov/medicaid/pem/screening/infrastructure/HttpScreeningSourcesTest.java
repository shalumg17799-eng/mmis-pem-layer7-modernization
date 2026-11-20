package gov.medicaid.pem.screening.infrastructure;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static gov.medicaid.pem.screening.ScreeningWorld.DMF;
import static gov.medicaid.pem.screening.ScreeningWorld.LEIE;
import static gov.medicaid.pem.screening.ScreeningWorld.LICENCE;
import static gov.medicaid.pem.screening.ScreeningWorld.SAM;
import static gov.medicaid.pem.screening.TestSources.MOCK;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import gov.medicaid.pem.screening.ScreeningWorld;
import gov.medicaid.pem.screening.TestSources;
import gov.medicaid.pem.screening.application.ProviderFacts;
import gov.medicaid.pem.screening.domain.Source;
import gov.medicaid.pem.screening.domain.SourceResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * MAM-20 and MAM-22 against the real HTTP client of the four sources (no Spring, no database; the
 * WireMock of TestSources plays the sources): one lookup and the configured number of retries, a
 * time limit that ends in C, the last failure deciding the detail, the owner lookup of an
 * organisation, and the four sources asked in parallel. Every test has its own time limit.
 */
@Timeout(30)
class HttpScreeningSourcesTest {

  static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);
  static final ProviderFacts RIVERA =
      new ProviderFacts("1003000111", "IN", "207Q00000X", "MD12345", "TX", "MARIA RIVERA", null);

  @BeforeEach
  void world() {
    MOCK.resetAll();
    TestSources.stubToken();
    ScreeningWorld.cleanSources();
    ScreeningWorld.licence("MD12345", "TX", "ACTIVE", "2028-09");
  }

  static HttpScreeningSources sources(Duration timeout, int retries) {
    return new HttpScreeningSources(MOCK.baseUrl(), timeout, retries, CLOCK);
  }

  static SourceResult of(List<SourceResult> results, Source source) {
    return results.stream().filter(r -> r.source() == source).findFirst().orElseThrow();
  }

  static ProviderFacts organisation(String type, String owner) {
    return new ProviderFacts(
        "1003000444", type, "251G00000X", null, null, "EXCLUDED CLINIC LLC", owner);
  }

  // ---- the usual answers ------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-18 AC-2] the four sources answer clean: four P results in the order of SCRNOUT, one lookup each")
  void clean() {
    List<SourceResult> r = sources(Duration.ofSeconds(2), 1).check(RIVERA);
    assertThat(r.stream().map(SourceResult::source))
        .containsExactly(Source.LICBOARD, Source.OIGLEIE, Source.SAMGOV, Source.SSADMF);
    assertThat(r).allSatisfy(x -> assertThat(x.result()).isEqualTo("P"));
    assertThat(r).allSatisfy(x -> assertThat(x.verdictAt()).isEqualTo(CLOCK.instant()));
    assertThat(ScreeningWorld.allLookups()).isEqualTo(4);
  }

  @Test
  @DisplayName("[MAM-18 AC-5] SAM is asked with the NPI and the name of the provider")
  void samGetsTheName() {
    sources(Duration.ofSeconds(2), 1).check(RIVERA);
    assertThat(
            MOCK.findAll(
                getRequestedFor(urlPathEqualTo(SAM))
                    .withQueryParam("npi", equalTo("1003000111"))
                    .withQueryParam("name", equalTo("MARIA RIVERA"))))
        .hasSize(1);
  }

  @Test
  @DisplayName("[MAM-18 AC-5] a provider without a name is looked up in SAM with an empty name")
  void samWithoutName() {
    ProviderFacts noName =
        new ProviderFacts("1003000111", "IN", "207Q00000X", "MD12345", "TX", null, null);
    sources(Duration.ofSeconds(2), 1).check(noName);
    assertThat(
            MOCK.findAll(getRequestedFor(urlPathEqualTo(SAM)).withQueryParam("name", equalTo(""))))
        .hasSize(1);
  }

  @Test
  @DisplayName("[MAM-18 AC-6] the sources report their hits: E201, E204 and E205 with the details")
  void hits() {
    ScreeningWorld.leieListed("1003000111");
    MOCK.stubFor(
        get(urlPathEqualTo(SAM))
            .atPriority(1)
            .withQueryParam("npi", equalTo("1003000111"))
            .willReturn(okJson("{\"source\":\"SAMGOV\",\"debarred\":true}")));
    MOCK.stubFor(
        get(urlPathEqualTo(DMF))
            .atPriority(1)
            .withQueryParam("npi", equalTo("1003000111"))
            .willReturn(
                okJson(
                    "{\"source\":\"SSADMF\",\"deceased\":true,\"dateOfDeath\":\"2025-08-01\"}")));
    List<SourceResult> r = sources(Duration.ofSeconds(2), 1).check(RIVERA);
    assertThat(of(r, Source.OIGLEIE).errorCode()).isEqualTo("E201");
    assertThat(of(r, Source.OIGLEIE).detail()).isEqualTo("EXCLUDED 1128(a)(1) SINCE 2023-01-15");
    assertThat(of(r, Source.SAMGOV).errorCode()).isEqualTo("E205");
    assertThat(of(r, Source.SSADMF).errorCode()).isEqualTo("E204");
    assertThat(of(r, Source.SSADMF).detail()).isEqualTo("DECEASED 2025-08-01");
  }

  // ---- MAM-20 AC-1: an error, and the retry -----------------------------------------------

  @ParameterizedTest(name = "[MAM-20 AC-1] {0} answers HTTP 500: one retry, then C")
  @EnumSource(Source.class)
  void errorIsRetriedOnceThenC(Source source) {
    String key = source == Source.LICBOARD ? "MD12345" : "1003000111";
    ScreeningWorld.sourceError(source.name(), key, 500);
    List<SourceResult> r = sources(Duration.ofSeconds(2), 1).check(RIVERA);
    SourceResult result = of(r, source);
    assertThat(result.result()).isEqualTo("C");
    assertThat(result.errorCode()).isNull();
    assertThat(result.detail()).isEqualTo("COULD NOT BE REACHED");
    assertThat(ScreeningWorld.lookups(source.name(), key)).isEqualTo(2);
    assertThat(r.stream().filter(x -> x.source() != source))
        .allSatisfy(x -> assertThat(x.result()).isEqualTo("P"));
  }

  @ParameterizedTest(name = "[MAM-20 AC-1] with {0} retries a failing source is asked {1} times")
  @CsvSource({"0,1", "1,2", "2,3", "3,4"})
  void retriesAreConfigurable(int retries, int asked) {
    ScreeningWorld.sourceError("SAMGOV", "1003000111", 503);
    SourceResult sam = of(sources(Duration.ofSeconds(2), retries).check(RIVERA), Source.SAMGOV);
    assertThat(sam.result()).isEqualTo("C");
    assertThat(ScreeningWorld.lookups("SAMGOV", "1003000111")).isEqualTo(asked);
  }

  @ParameterizedTest(
      name = "[MAM-20 AC-1] HTTP {0} from the source is no answer: C after the retry")
  @CsvSource({"400", "401", "403", "404", "429", "500", "502", "503", "504"})
  void anyErrorStatusIsNoAnswer(int status) {
    ScreeningWorld.sourceError("SSADMF", "1003000111", status);
    SourceResult dmf = of(sources(Duration.ofSeconds(2), 1).check(RIVERA), Source.SSADMF);
    assertThat(dmf.result()).isEqualTo("C");
    assertThat(dmf.detail()).isEqualTo("COULD NOT BE REACHED");
    assertThat(ScreeningWorld.lookups("SSADMF", "1003000111")).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "[MAM-20 AC-1] a source that fails once and then answers is judged on its answer: P, two lookups")
  void retrySucceeds() {
    MOCK.stubFor(
        get(urlPathEqualTo(LEIE))
            .atPriority(1)
            .inScenario("leie-flaky")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("second")
            .willReturn(aResponse().withStatus(500)));
    MOCK.stubFor(
        get(urlPathEqualTo(LEIE))
            .atPriority(1)
            .inScenario("leie-flaky")
            .whenScenarioStateIs("second")
            .willReturn(okJson("{\"source\":\"OIGLEIE\",\"excluded\":false}")));
    SourceResult leie = of(sources(Duration.ofSeconds(2), 1).check(RIVERA), Source.OIGLEIE);
    assertThat(leie.result()).isEqualTo("P");
    assertThat(ScreeningWorld.lookups("OIGLEIE", "1003000111")).isEqualTo(2);
  }

  @ParameterizedTest(
      name = "[MAM-20 AC-1] a {0} of the connection is no answer: C, COULD NOT BE REACHED")
  @EnumSource(
      value = Fault.class,
      names = {"CONNECTION_RESET_BY_PEER", "EMPTY_RESPONSE", "MALFORMED_RESPONSE_CHUNK"})
  void connectionFaults(Fault fault) {
    ScreeningWorld.sourceFault("SAMGOV", "1003000111", fault);
    SourceResult sam = of(sources(Duration.ofSeconds(2), 1).check(RIVERA), Source.SAMGOV);
    assertThat(sam.result()).isEqualTo("C");
    assertThat(sam.errorCode()).isNull();
    assertThat(sam.detail()).isEqualTo("COULD NOT BE REACHED");
  }

  @Test
  @DisplayName("[MAM-20 AC-1] an HTTP 200 whose body is not JSON is no answer: C")
  void notJson() {
    MOCK.stubFor(
        get(urlPathEqualTo(DMF))
            .atPriority(1)
            .withQueryParam("npi", equalTo("1003000111"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("<html>oops</html>")));
    SourceResult dmf = of(sources(Duration.ofSeconds(2), 1).check(RIVERA), Source.SSADMF);
    assertThat(dmf.result()).isEqualTo("C");
    assertThat(dmf.detail()).isEqualTo("COULD NOT BE REACHED");
  }

  @Test
  @DisplayName("[MAM-20 AC-1] an HTTP 200 without any body is no answer: C, never P")
  void emptyBody() {
    MOCK.stubFor(
        get(urlPathEqualTo(DMF))
            .atPriority(1)
            .withQueryParam("npi", equalTo("1003000111"))
            .willReturn(aResponse().withStatus(200)));
    SourceResult dmf = of(sources(Duration.ofSeconds(2), 1).check(RIVERA), Source.SSADMF);
    assertThat(dmf.result()).isEqualTo("C");
    assertThat(ScreeningWorld.lookups("SSADMF", "1003000111")).isEqualTo(2);
  }

  @Test
  @DisplayName("[MAM-20 AC-1] a source nobody mocked (HTTP 404 from the server) is no answer: C")
  void unknownPath() {
    MOCK.resetAll();
    TestSources.stubToken();
    List<SourceResult> r = sources(Duration.ofSeconds(2), 1).check(RIVERA);
    assertThat(r).allSatisfy(x -> assertThat(x.result()).isEqualTo("C"));
  }

  // ---- MAM-20 AC-2: slow ------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-20 AC-2] a source slower than its limit gives C, DID NOT ANSWER IN TIME, after two attempts")
  void slowIsTimedOut() {
    ScreeningWorld.sourceSlow("SAMGOV", "1003000111", 1500);
    long start = System.nanoTime();
    SourceResult sam = of(sources(Duration.ofMillis(300), 1).check(RIVERA), Source.SAMGOV);
    Duration took = Duration.ofNanos(System.nanoTime() - start);
    assertThat(sam.result()).isEqualTo("C");
    assertThat(sam.errorCode()).isNull();
    assertThat(sam.detail()).isEqualTo("DID NOT ANSWER IN TIME");
    assertThat(ScreeningWorld.lookups("SAMGOV", "1003000111")).isEqualTo(2);
    assertThat(took).isLessThan(Duration.ofMillis(1400));
  }

  @Test
  @DisplayName("[MAM-20 AC-2] a source that answers within its limit is used, without a retry")
  void slowButInTime() {
    ScreeningWorld.sourceSlow("SAMGOV", "1003000111", 300);
    SourceResult sam = of(sources(Duration.ofSeconds(2), 1).check(RIVERA), Source.SAMGOV);
    assertThat(sam.result()).isEqualTo("P");
    assertThat(ScreeningWorld.lookups("SAMGOV", "1003000111")).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-20 AC-2] slow on the first attempt and quick on the retry: judged on the answer, P")
  void slowThenQuick() {
    MOCK.stubFor(
        get(urlPathEqualTo(SAM))
            .atPriority(1)
            .inScenario("sam-slow-once")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("second")
            .willReturn(okJson("{\"source\":\"SAMGOV\",\"debarred\":false}").withFixedDelay(1200)));
    MOCK.stubFor(
        get(urlPathEqualTo(SAM))
            .atPriority(1)
            .inScenario("sam-slow-once")
            .whenScenarioStateIs("second")
            .willReturn(okJson("{\"source\":\"SAMGOV\",\"debarred\":false}")));
    SourceResult sam = of(sources(Duration.ofMillis(300), 1).check(RIVERA), Source.SAMGOV);
    assertThat(sam.result()).isEqualTo("P");
    assertThat(ScreeningWorld.lookups("SAMGOV", "1003000111")).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "[MAM-20 AC-2] the last attempt decides the detail: timed out, then an error is COULD NOT BE REACHED")
  void timeoutThenErrorIsUnreachable() {
    MOCK.stubFor(
        get(urlPathEqualTo(SAM))
            .atPriority(1)
            .inScenario("sam-timeout-error")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("second")
            .willReturn(okJson("{\"debarred\":false}").withFixedDelay(1200)));
    MOCK.stubFor(
        get(urlPathEqualTo(SAM))
            .atPriority(1)
            .inScenario("sam-timeout-error")
            .whenScenarioStateIs("second")
            .willReturn(aResponse().withStatus(500)));
    SourceResult sam = of(sources(Duration.ofMillis(300), 1).check(RIVERA), Source.SAMGOV);
    assertThat(sam.result()).isEqualTo("C");
    assertThat(sam.detail()).isEqualTo("COULD NOT BE REACHED");
  }

  @Test
  @DisplayName(
      "[MAM-20 AC-2] the last attempt decides the detail: an error, then a timeout is DID NOT ANSWER IN TIME")
  void errorThenTimeoutIsTimedOut() {
    MOCK.stubFor(
        get(urlPathEqualTo(SAM))
            .atPriority(1)
            .inScenario("sam-error-timeout")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("second")
            .willReturn(aResponse().withStatus(500)));
    MOCK.stubFor(
        get(urlPathEqualTo(SAM))
            .atPriority(1)
            .inScenario("sam-error-timeout")
            .whenScenarioStateIs("second")
            .willReturn(okJson("{\"debarred\":false}").withFixedDelay(1200)));
    SourceResult sam = of(sources(Duration.ofMillis(300), 1).check(RIVERA), Source.SAMGOV);
    assertThat(sam.result()).isEqualTo("C");
    assertThat(sam.detail()).isEqualTo("DID NOT ANSWER IN TIME");
  }

  @Test
  @DisplayName(
      "[MAM-20 AC-2] the four sources are asked in parallel: four slow answers cost one wait, not four")
  void parallel() {
    for (String s : ScreeningWorld.SOURCES) {
      ScreeningWorld.sourceSlow(s, "LICBOARD".equals(s) ? "MD12345" : "1003000111", 800);
    }
    long start = System.nanoTime();
    List<SourceResult> r = sources(Duration.ofSeconds(3), 1).check(RIVERA);
    Duration took = Duration.ofNanos(System.nanoTime() - start);
    assertThat(r).allSatisfy(x -> assertThat(x.result()).isEqualTo("P"));
    assertThat(took).isLessThan(Duration.ofMillis(2400));
  }

  // ---- MAM-20 AC-3 and AC-4: the licence --------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-20 AC-3] a licence the board does not know: F without a code, LICENCE NOT FOUND")
  void licenceUnknown() {
    ProviderFacts unknown =
        new ProviderFacts(
            "1003000666", "IN", "207Q00000X", "XX00000", "TX", "TESTPROV FOXTROT", null);
    SourceResult lic = of(sources(Duration.ofSeconds(2), 1).check(unknown), Source.LICBOARD);
    assertThat(lic.result()).isEqualTo("F");
    assertThat(lic.errorCode()).isNull();
    assertThat(lic.detail()).isEqualTo("LICENCE NOT FOUND");
    assertThat(ScreeningWorld.lookups("LICBOARD", "XX00000")).isEqualTo(1);
  }

  @ParameterizedTest(
      name = "[MAM-20 AC-3] an individual with licence number <{0}> fails without asking the board")
  @CsvSource(
      value = {"NIL", "''", "'   '"},
      nullValues = "NIL")
  void noLicenceNumber(String licence) {
    ProviderFacts none =
        new ProviderFacts(
            "1003000666", "IN", "207Q00000X", licence, "TX", "TESTPROV FOXTROT", null);
    SourceResult lic = of(sources(Duration.ofSeconds(2), 1).check(none), Source.LICBOARD);
    assertThat(lic.result()).isEqualTo("F");
    assertThat(lic.errorCode()).isNull();
    assertThat(lic.detail()).isEqualTo("LICENCE NOT FOUND");
    assertThat(ScreeningWorld.lookups("LICBOARD")).isZero();
  }

  @ParameterizedTest(
      name = "[MAM-20 AC-4] a {0} is not checked against a licence, whatever its record holds")
  @CsvSource({"GR", "FA"})
  void organisationHasNoLicenceCheck(String type) {
    ProviderFacts org =
        new ProviderFacts(
            "1003000333", type, "251G00000X", "GR11100", "TX", "SUNSET HOSPICE INC", null);
    List<SourceResult> r = sources(Duration.ofSeconds(2), 1).check(org);
    SourceResult lic = of(r, Source.LICBOARD);
    assertThat(lic.result()).isEqualTo("P");
    assertThat(lic.detail()).isEqualTo("NOT APPLICABLE");
    assertThat(lic.errorCode()).isNull();
    assertThat(ScreeningWorld.lookups("LICBOARD")).isZero();
    assertThat(ScreeningWorld.lookups("OIGLEIE", "1003000333")).isEqualTo(1);
    assertThat(ScreeningWorld.lookups("SAMGOV", "1003000333")).isEqualTo(1);
    assertThat(ScreeningWorld.lookups("SSADMF", "1003000333")).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-20 AC-3] a licence with a status other than ACTIVE and EXPIRED fails without a code")
  void suspendedLicence() {
    ScreeningWorld.licence("MD12345", "TX", "SUSPENDED", "2028-09");
    SourceResult lic = of(sources(Duration.ofSeconds(2), 1).check(RIVERA), Source.LICBOARD);
    assertThat(lic.result()).isEqualTo("F");
    assertThat(lic.errorCode()).isNull();
    assertThat(lic.detail()).isEqualTo("LICENCE MD12345 TX SUSPENDED");
  }

  @Test
  @DisplayName("[MAM-20 AC-3] a licence without a state is asked with an empty state")
  void licenceWithoutState() {
    ProviderFacts noState =
        new ProviderFacts("1003000111", "IN", "207Q00000X", "MD12345", null, "MARIA RIVERA", null);
    sources(Duration.ofSeconds(2), 1).check(noState);
    assertThat(
            MOCK.findAll(
                getRequestedFor(urlPathEqualTo(LICENCE))
                    .withQueryParam("licenseNo", equalTo("MD12345"))
                    .withQueryParam("state", equalTo(""))))
        .hasSize(1);
  }

  // ---- MAM-22: the owner ------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-22 AC-2] an organisation with an owner: the LEIE is asked for the provider and for the owner, once each")
  void bothAreAsked() {
    sources(Duration.ofSeconds(2), 1).check(organisation("GR", "1990000999"));
    assertThat(ScreeningWorld.lookups("OIGLEIE", "1003000444")).isEqualTo(1);
    assertThat(ScreeningWorld.lookups("OIGLEIE", "1990000999")).isEqualTo(1);
    assertThat(ScreeningWorld.lookups("OIGLEIE")).isEqualTo(2);
  }

  @ParameterizedTest(
      name = "[MAM-22 AC-3] an owner NPI <{0}> is no owner: one lookup, the provider's own NPI")
  @CsvSource(
      value = {"NIL", "''", "'  '"},
      nullValues = "NIL")
  void noOwnerNoSecondLookup(String owner) {
    SourceResult leie =
        of(sources(Duration.ofSeconds(2), 1).check(organisation("FA", owner)), Source.OIGLEIE);
    assertThat(leie.result()).isEqualTo("P");
    assertThat(ScreeningWorld.lookups("OIGLEIE")).isEqualTo(1);
    assertThat(ScreeningWorld.lookups("OIGLEIE", "1003000444")).isEqualTo(1);
  }

  @Test
  @DisplayName("[MAM-22 AC-1] only the owner is on the LEIE: E203 naming the owner")
  void ownerExcluded() {
    ScreeningWorld.leieListed("1990000111");
    SourceResult leie =
        of(
            sources(Duration.ofSeconds(2), 1).check(organisation("GR", "1990000111")),
            Source.OIGLEIE);
    assertThat(leie.result()).isEqualTo("F");
    assertThat(leie.errorCode()).isEqualTo("E203");
    assertThat(leie.detail()).isEqualTo("OWNER 1990000111 EXCLUDED 1128(a)(1) SINCE 2023-01-15");
  }

  @Test
  @DisplayName(
      "[MAM-22 AC-4] the provider and its owner are on the LEIE: one result, E201, both named")
  void bothExcluded() {
    ScreeningWorld.leieListed("1990000111");
    ScreeningWorld.leieListed("1003000444");
    List<SourceResult> r =
        sources(Duration.ofSeconds(2), 1).check(organisation("GR", "1990000111"));
    assertThat(r.stream().filter(x -> x.source() == Source.OIGLEIE)).hasSize(1);
    SourceResult leie = of(r, Source.OIGLEIE);
    assertThat(leie.errorCode()).isEqualTo("E201");
    assertThat(leie.detail()).contains("1003000444").contains("1990000111");
  }

  @Test
  @DisplayName(
      "[MAM-22 AC-5] the owner lookup fails: two lookups of the owner, one of the provider, C without a code")
  void ownerLookupFails() {
    ScreeningWorld.sourceError("OIGLEIE", "1990000999", 500);
    SourceResult leie =
        of(
            sources(Duration.ofSeconds(2), 1).check(organisation("GR", "1990000999")),
            Source.OIGLEIE);
    assertThat(leie.result()).isEqualTo("C");
    assertThat(leie.errorCode()).isNull();
    assertThat(leie.detail()).isEqualTo("COULD NOT BE REACHED");
    assertThat(ScreeningWorld.lookups("OIGLEIE", "1990000999")).isEqualTo(2);
    assertThat(ScreeningWorld.lookups("OIGLEIE", "1003000444")).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-22 AC-5] the provider lookup fails and the owner is clean: C, the owner is still asked once")
  void ownLookupFails() {
    ScreeningWorld.sourceError("OIGLEIE", "1003000444", 500);
    SourceResult leie =
        of(
            sources(Duration.ofSeconds(2), 1).check(organisation("GR", "1990000999")),
            Source.OIGLEIE);
    assertThat(leie.result()).isEqualTo("C");
    assertThat(ScreeningWorld.lookups("OIGLEIE", "1003000444")).isEqualTo(2);
    assertThat(ScreeningWorld.lookups("OIGLEIE", "1990000999")).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-22 AC-5] the provider is on the LEIE but the owner lookup fails: C (question C-5 default), the exclusion is not hidden by a P")
  void listedProviderAndFailingOwner() {
    ScreeningWorld.leieListed("1003000444");
    ScreeningWorld.sourceError("OIGLEIE", "1990000999", 500);
    SourceResult leie =
        of(
            sources(Duration.ofSeconds(2), 1).check(organisation("GR", "1990000999")),
            Source.OIGLEIE);
    assertThat(leie.result()).isNotEqualTo("P");
    assertThat(leie.result()).isEqualTo("C");
  }

  @Test
  @DisplayName(
      "[MAM-22 AC-5] an owner lookup that does not answer in time is DID NOT ANSWER IN TIME")
  void ownerLookupSlow() {
    ScreeningWorld.sourceSlow("OIGLEIE", "1990000999", 1200);
    SourceResult leie =
        of(
            sources(Duration.ofMillis(300), 1).check(organisation("GR", "1990000999")),
            Source.OIGLEIE);
    assertThat(leie.result()).isEqualTo("C");
    assertThat(leie.detail()).isEqualTo("DID NOT ANSWER IN TIME");
  }

  @Test
  @DisplayName("[MAM-22 AC-2] the owner lookup runs beside the provider lookup, not after it")
  void ownerInParallel() {
    ScreeningWorld.sourceSlow("OIGLEIE", "1003000444", 1500);
    ScreeningWorld.sourceSlow("OIGLEIE", "1990000999", 1500);
    long start = System.nanoTime();
    SourceResult leie =
        of(
            sources(Duration.ofSeconds(4), 1).check(organisation("GR", "1990000999")),
            Source.OIGLEIE);
    Duration took = Duration.ofNanos(System.nanoTime() - start);
    assertThat(leie.result()).isEqualTo("P");
    assertThat(took).isLessThan(Duration.ofMillis(2600));
  }
}
