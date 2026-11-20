package gov.medicaid.pem.screening.infrastructure;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static gov.medicaid.pem.screening.TestSources.MOCK;
import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.screening.ScreeningWorld;
import gov.medicaid.pem.screening.TestSources;
import gov.medicaid.pem.screening.application.ProviderFacts;
import gov.medicaid.pem.screening.domain.Source;
import gov.medicaid.pem.screening.domain.SourceResult;
import gov.medicaid.pem.screening.domain.Verdict;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * MAM-20 AC-1 (D-024, question C-1): nobody is passed on an answer that was never received. A
 * source that answers HTTP 200 with a document that holds no verdict (an empty object, an error
 * document, an array, a text, or the verdict member null) has given no answer to the question that
 * was asked: the result is C without an error code, never P (and never the F of a hit), so the
 * screening fails and an analyst can run it again. The same holds for the lookup of an owner.
 */
@Timeout(30)
class SourceAnswerShapeTest {

  static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);
  static final ProviderFacts RIVERA =
      new ProviderFacts("1003000111", "IN", "207Q00000X", "MD12345", "TX", "MARIA RIVERA", null);
  static final ProviderFacts GROUP =
      new ProviderFacts(
          "1003000222", "GR", "251G00000X", null, null, "NORTHSIDE CLINIC GR", "1990000999");

  @BeforeEach
  void world() {
    MOCK.resetAll();
    TestSources.stubToken();
    ScreeningWorld.cleanSources();
    ScreeningWorld.licence("MD12345", "TX", "ACTIVE", "2028-09");
  }

  static HttpScreeningSources sources() {
    return new HttpScreeningSources(MOCK.baseUrl(), Duration.ofSeconds(2), 1, CLOCK);
  }

  static SourceResult of(List<SourceResult> results, Source source) {
    return results.stream().filter(r -> r.source() == source).findFirst().orElseThrow();
  }

  /** The member of each source's answer that holds the verdict. */
  static String verdictMember(Source source) {
    return switch (source) {
      case LICBOARD -> "found";
      case OIGLEIE -> "excluded";
      case SAMGOV -> "debarred";
      case SSADMF -> "deceased";
    };
  }

  static Stream<Arguments> bodiesWithoutVerdict() {
    return Stream.of(Source.values())
        .flatMap(
            source ->
                Stream.of(
                        "{}",
                        "{\"source\":\"" + source + "\",\"error\":\"stubbed server error\"}",
                        "[]",
                        "\"ok\"",
                        "{\"" + verdictMember(source) + "\":null}")
                    .map(body -> Arguments.of(source, body)));
  }

  @ParameterizedTest(
      name =
          "[MAM-20 AC-1] {0} answers HTTP 200 with <{1}>, which holds no verdict: C, no error code, never a pass")
  @MethodSource("bodiesWithoutVerdict")
  void answerWithoutVerdictIsNoAnswer(Source source, String body) {
    MOCK.stubFor(
        get(urlPathEqualTo(ScreeningWorld.pathOf(source.name())))
            .atPriority(1)
            .withQueryParam(
                ScreeningWorld.keyOf(source.name()),
                equalTo("LICBOARD".equals(source.name()) ? "MD12345" : "1003000111"))
            .willReturn(okJson(body)));
    List<SourceResult> results = sources().check(RIVERA);
    SourceResult r = of(results, source);
    assertThat(r.result()).as("result of %s for %s", source, body).isEqualTo("C");
    assertThat(r.errorCode()).isNull();
    assertThat(Verdict.of(results)).isEqualTo("FAIL");
  }

  @Test
  @DisplayName(
      "[MAM-20 AC-1] the other three sources are still recorded with their own answers when one gives an answer without a verdict")
  void otherSourcesAreStillRecorded() {
    MOCK.stubFor(
        get(urlPathEqualTo(ScreeningWorld.LEIE))
            .atPriority(1)
            .withQueryParam("npi", equalTo("1003000111"))
            .willReturn(okJson("{}")));
    List<SourceResult> results = sources().check(RIVERA);
    assertThat(of(results, Source.OIGLEIE).result()).isEqualTo("C");
    assertThat(of(results, Source.LICBOARD).result()).isEqualTo("P");
    assertThat(of(results, Source.SAMGOV).result()).isEqualTo("P");
    assertThat(of(results, Source.SSADMF).result()).isEqualTo("P");
  }

  @ParameterizedTest(
      name =
          "[MAM-22 AC-5] the LEIE answers the lookup of the owner with <{0}>, which holds no verdict: OIGLEIE is C, never a pass")
  @MethodSource("ownerBodies")
  void ownerAnswerWithoutVerdictIsNoAnswer(String body) {
    MOCK.stubFor(
        get(urlPathEqualTo(ScreeningWorld.LEIE))
            .atPriority(1)
            .withQueryParam("npi", equalTo("1990000999"))
            .willReturn(okJson(body)));
    SourceResult r = of(sources().check(GROUP), Source.OIGLEIE);
    assertThat(r.result()).isEqualTo("C");
    assertThat(r.errorCode()).isNull();
  }

  static Stream<String> ownerBodies() {
    return Stream.of("{}", "{\"error\":\"stubbed server error\"}", "[]", "{\"excluded\":null}");
  }
}
