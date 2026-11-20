package gov.medicaid.pem.screening.infrastructure;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static gov.medicaid.pem.screening.ScreeningWorld.SAM;
import static gov.medicaid.pem.screening.TestSources.MOCK;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import gov.medicaid.pem.screening.LogCapture;
import gov.medicaid.pem.screening.ScreeningWorld;
import gov.medicaid.pem.screening.TestSources;
import gov.medicaid.pem.screening.application.ProviderFacts;
import gov.medicaid.pem.screening.domain.Source;
import gov.medicaid.pem.screening.domain.SourceResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;

/**
 * MAM-20 AC-2 under repetition: a source that does not answer within its limit is recorded with the
 * detail DID NOT ANSWER IN TIME every time, not only most of the time. The detail decides whether an
 * analyst sees "slow" or "down"; it must not depend on which way the HTTP client of the JDK
 * reports the cancelled call. Both ways to reach the last attempt are repeated: two attempts that
 * time out, and an error answer followed by an attempt that times out (the connection of the first
 * answer is reused). The WARN lines the service logged for the failing repetition are in the
 * message of the failure.
 */
@Timeout(120)
class TimeoutDetailRepeatedTest {

  static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);
  static final ProviderFacts RIVERA =
      new ProviderFacts("1003000111", "IN", "207Q00000X", "MD12345", "TX", "MARIA RIVERA", null);
  static final String TIMED_OUT = "DID NOT ANSWER IN TIME";

  @BeforeEach
  void world() {
    MOCK.resetAll();
    TestSources.stubToken();
    ScreeningWorld.cleanSources();
    ScreeningWorld.licence("MD12345", "TX", "ACTIVE", "2028-09");
  }

  static HttpScreeningSources sources() {
    return new HttpScreeningSources(MOCK.baseUrl(), Duration.ofMillis(300), 1, CLOCK);
  }

  /** SAM is asked about the provider: C, no error code, the detail of a timeout; the WARN lines are shown on failure. */
  static void assertTimedOut() {
    try (LogCapture log = LogCapture.open()) {
      SourceResult sam =
          sources().check(RIVERA).stream()
              .filter(r -> r.source() == Source.SAMGOV)
              .findFirst()
              .orElseThrow();
      assertThat(sam.result()).isEqualTo("C");
      assertThat(sam.errorCode()).isNull();
      assertThat(sam.detail())
          .as("detail of SAMGOV; the WARN lines of the attempts: %s", log.messages(Level.WARN))
          .isEqualTo(TIMED_OUT);
    }
  }

  @RepeatedTest(
      value = 30,
      name =
          "[MAM-20 AC-2] repetition {currentRepetition} of {totalRepetitions}: two attempts that time out are DID NOT ANSWER IN TIME")
  void twoTimeouts() {
    ScreeningWorld.sourceSlow("SAMGOV", "1003000111", 1200);
    assertTimedOut();
  }

  @RepeatedTest(
      value = 30,
      name =
          "[MAM-20 AC-2] repetition {currentRepetition} of {totalRepetitions}: an error, then an attempt that times out, is DID NOT ANSWER IN TIME")
  void errorThenTimeout() {
    MOCK.stubFor(
        get(urlPathEqualTo(SAM))
            .atPriority(1)
            .inScenario("sam-error-timeout-repeated")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("second")
            .willReturn(aResponse().withStatus(500)));
    MOCK.stubFor(
        get(urlPathEqualTo(SAM))
            .atPriority(1)
            .inScenario("sam-error-timeout-repeated")
            .whenScenarioStateIs("second")
            .willReturn(okJson("{\"debarred\":false}").withFixedDelay(1200)));
    assertTimedOut();
  }
}
