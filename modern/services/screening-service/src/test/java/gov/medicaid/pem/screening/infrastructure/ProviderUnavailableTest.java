package gov.medicaid.pem.screening.infrastructure;

import static gov.medicaid.pem.screening.TestSources.MOCK;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import gov.medicaid.pem.screening.Lane3Test;
import gov.medicaid.pem.screening.ScreeningWorld;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * MAM-20 AC-5 on the real Kafka: a provider that cannot be read (provider-service answers 5xx or
 * does not answer) is not screened on made-up data. The event that asked for the screening is not
 * parked on the DLT after the usual retries: it is retried, with a pause that grows to a limit,
 * until provider-service answers (D-024, ADR-003). While it fails nothing is stored, announced or
 * asked of a source and the event is not recorded as processed; once provider-service is back,
 * exactly one COMPLETED screening and one ScreeningCompleted exist. Every test has its own time
 * limit.
 */
@Timeout(value = 150, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ProviderUnavailableTest extends Lane3Test {

  static final String NPI = "1003000777";

  /** How provider-service does not answer. */
  enum Down {
    HTTP_500,
    HTTP_502,
    HTTP_503,
    HTTP_504,
    CONNECTION_RESET,
    EMPTY_RESPONSE
  }

  StubMapping breakIt(Down how) {
    return switch (how) {
      case HTTP_500 -> ScreeningWorld.providerAnswers(NPI, 500);
      case HTTP_502 -> ScreeningWorld.providerAnswers(NPI, 502);
      case HTTP_503 -> ScreeningWorld.providerAnswers(NPI, 503);
      case HTTP_504 -> ScreeningWorld.providerAnswers(NPI, 504);
      case CONNECTION_RESET -> {
        ScreeningWorld.providerFault(NPI, Fault.CONNECTION_RESET_BY_PEER);
        yield null;
      }
      case EMPTY_RESPONSE -> {
        ScreeningWorld.providerFault(NPI, Fault.EMPTY_RESPONSE);
        yield null;
      }
    };
  }

  /** Puts provider-service back: the failing stub goes, the provider is known again. */
  void restore(StubMapping failing) {
    if (failing != null) {
      MOCK.removeStubMapping(failing);
    } else {
      MOCK.resetAll();
      gov.medicaid.pem.screening.TestSources.stubToken();
      ScreeningWorld.cleanSources();
    }
    ScreeningWorld.holds(ScreeningWorld.individual(NPI, "TESTPROV", "ECHO", "MD77777", "TX"));
    ScreeningWorld.licence("MD77777", "TX", "ACTIVE", "2029-01", 3);
  }

  @ParameterizedTest(
      name =
          "[MAM-20 AC-5] provider-service fails with {0}: nothing is screened, announced or asked meanwhile; one screening when it is back")
  @EnumSource(Down.class)
  void retriedUntilRestored(Down how) throws Exception {
    StubMapping failing = breakIt(how);
    UUID event = providerRegistered(NPI);
    until(
        "provider-service is asked about " + NPI + " at least 3 times",
        () -> ScreeningWorld.providerReads(NPI) >= 3,
        BOUND);
    assertThat(screeningCount(NPI)).as("screenings while provider-service fails").isZero();
    assertThat(completedEvents(NPI)).as("ScreeningCompleted while provider-service fails").isZero();
    assertThat(ScreeningWorld.allLookups()).as("no source is asked about made-up data").isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM processed_event WHERE event_id = ?", Integer.class, event))
        .as("the event is not recorded as processed while it fails")
        .isZero();
    restore(failing);
    awaitProcessed(event);
    assertThat(screeningCount(NPI)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM screening WHERE npi = ? AND status = 'COMPLETED' AND reason = 'INITIAL'",
                Integer.class,
                NPI))
        .isEqualTo(1);
    assertThat(completedEvents(NPI)).isEqualTo(1);
    assertThat(
            noRaw(
                "pem.provider.events.DLT",
                text -> text.contains(event.toString()),
                java.time.Duration.ofSeconds(3)))
        .as("the event was never parked on the DLT")
        .isTrue();
  }

  @Test
  @DisplayName(
      "[MAM-20 AC-5] provider-service that does not answer within its limit is retried too: one screening when it is back")
  void hangIsRetriedUntilRestored() throws Exception {
    ScreeningWorld.providerSlow(
        ScreeningWorld.individual(NPI, "TESTPROV", "ECHO", "MD77777", "TX"), 15_000);
    UUID event = providerRegistered(NPI);
    until(
        "provider-service is asked about " + NPI + " at least twice",
        () -> ScreeningWorld.providerReads(NPI) >= 2,
        BOUND);
    assertThat(screeningCount(NPI)).isZero();
    assertThat(completedEvents(NPI)).isZero();
    restore(null);
    awaitProcessed(event);
    assertThat(screeningCount(NPI)).isEqualTo(1);
    assertThat(completedEvents(NPI)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-20 AC-5] a RevalidationDue whose provider cannot be read is retried the same way: one REVALIDATION screening when it is back")
  void revalidationIsRetriedToo() throws Exception {
    StubMapping failing = breakIt(Down.HTTP_503);
    UUID event = revalidationDue(NPI, "2026-02-28");
    until(
        "provider-service is asked about " + NPI + " at least 3 times",
        () -> ScreeningWorld.providerReads(NPI) >= 3,
        BOUND);
    assertThat(screeningCount(NPI)).isZero();
    restore(failing);
    awaitProcessed(event);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM screening WHERE npi = ? AND reason = 'REVALIDATION'",
                Integer.class,
                NPI))
        .isEqualTo(1);
    assertThat(completedEvents(NPI)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-20 AC-5] a provider-service that fails with a client error (400) is not retried for ever: the event is parked on the DLT after the usual retries")
  void clientErrorIsParked() throws Exception {
    ScreeningWorld.providerAnswers(NPI, 400);
    UUID event = providerRegistered(NPI);
    awaitRaw("pem.provider.events.DLT", text -> text.contains(event.toString()));
    assertThat(screeningCount(NPI)).isZero();
    assertThat(completedEvents(NPI)).isZero();
    assertThat(ScreeningWorld.providerReads(NPI)).isEqualTo(3);
  }

  @Test
  @DisplayName(
      "[MAM-20 AC-5] a provider that could not be read and then no longer exists (404) is parked on the DLT, not retried for ever, and a later event is processed")
  void unreadableThenGoneIsParked() throws Exception {
    StubMapping failing = breakIt(Down.HTTP_503);
    UUID event = providerRegistered(NPI);
    until(
        "provider-service is asked about " + NPI + " at least 3 times",
        () -> ScreeningWorld.providerReads(NPI) >= 3,
        BOUND);
    MOCK.removeStubMapping(failing);
    ScreeningWorld.providerUnknown(NPI);
    try {
      awaitRaw("pem.provider.events.DLT", text -> text.contains(event.toString()));
    } finally {
      // never leave the partition blocked: an event that is still retried then succeeds
      MOCK.resetAll();
      gov.medicaid.pem.screening.TestSources.stubToken();
      ScreeningWorld.cleanSources();
      ScreeningWorld.holds(ScreeningWorld.individual(NPI, "TESTPROV", "ECHO", "MD77777", "TX"));
      ScreeningWorld.licence("MD77777", "TX", "ACTIVE", "2029-01", 3);
      ScreeningWorld.holds(
          ScreeningWorld.individual("1003000888", "TESTPROV", "FOXTROT", "MD77777", "TX"));
    }
    UUID later = providerRegistered("1003000888");
    awaitProcessed(later);
    assertThat(screeningCount("1003000888")).isEqualTo(1);
  }
}
