package gov.medicaid.pem.screening.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.screening.Lane3Test;
import gov.medicaid.pem.screening.ScreeningWorld;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * MAM-20 AC-2 measured on the wall clock (nfr.md, Performance: screening of one provider under 5
 * seconds): from the moment the event is sent to the moment the screening is COMPLETED, not only
 * between the two times the screening records for itself. One source answers only after 3 seconds,
 * the limit is 2 seconds with one retry, so the screening waits for 2 attempts of 2 seconds and is
 * then COMPLETED with C for that source. A warm-up screening comes first, so the time of the first
 * connection of the Kafka consumer is not counted. Every wait has its own bound.
 */
@Timeout(value = 90, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class SlowSourceBudgetTest extends Lane3Test {

  static final String NPI = "1003000111";
  static final String WARM = "1003000999";
  static final Duration BUDGET = Duration.ofSeconds(5);

  void providers() {
    ScreeningWorld.holds(ScreeningWorld.individual(NPI, "MARIA", "RIVERA", "MD12345", "TX"));
    ScreeningWorld.licence("MD12345", "TX", "ACTIVE", "2028-09", 3);
    ScreeningWorld.holds(ScreeningWorld.individual(WARM, "TESTPROV", "WARMUP", "MD99999", "TX"));
    ScreeningWorld.licence("MD99999", "TX", "ACTIVE", "2028-09", 3);
    awaitProcessed(providerRegistered(WARM));
  }

  @ParameterizedTest(
      name =
          "[MAM-20 AC-2] {0} that answers after 3 seconds: the screening is COMPLETED within 5 seconds of the event")
  @CsvSource({"LICBOARD,MD12345", "OIGLEIE,1003000111", "SAMGOV,1003000111", "SSADMF,1003000111"})
  void completedWithinFiveSecondsOfTheEvent(String source, String key) {
    providers();
    ScreeningWorld.sourceSlow(source, key, 3000);
    long sent = System.nanoTime();
    UUID event = providerRegistered(NPI);
    awaitCompleted(NPI, 1);
    Duration took = Duration.ofNanos(System.nanoTime() - sent);
    assertThat(took).as("event sent to screening COMPLETED").isLessThan(BUDGET);
    awaitProcessed(event);
  }

  @Test
  @DisplayName(
      "[MAM-20 AC-2] a requested screening with a slow source is COMPLETED within 5 seconds of the request")
  void requestedScreeningWithinFiveSeconds() throws Exception {
    providers();
    ScreeningWorld.sourceSlow("SAMGOV", NPI, 3000);
    long sent = System.nanoTime();
    JsonNode done = screen(NPI);
    Duration took = Duration.ofNanos(System.nanoTime() - sent);
    assertThat(took).as("request to screening COMPLETED").isLessThan(BUDGET);
    assertThat(done.path("result").asText()).isEqualTo("FAIL");
  }

  @Test
  @DisplayName(
      "[MAM-20 AC-2] all four sources slow at once: still COMPLETED within 5 seconds, the sources are waited for in parallel")
  void allFourSlow() {
    providers();
    ScreeningWorld.sourceSlow("LICBOARD", "MD12345", 3000);
    ScreeningWorld.sourceSlow("OIGLEIE", NPI, 3000);
    ScreeningWorld.sourceSlow("SAMGOV", NPI, 3000);
    ScreeningWorld.sourceSlow("SSADMF", NPI, 3000);
    long sent = System.nanoTime();
    providerRegistered(NPI);
    awaitCompleted(NPI, 1);
    assertThat(Duration.ofNanos(System.nanoTime() - sent)).isLessThan(BUDGET);
    int notAnswered =
        jdbc.queryForObject(
            "SELECT count(*) FROM screening_result r JOIN screening s ON s.id = r.screening_id"
                + " WHERE s.npi = ? AND r.result = 'C' AND r.detail = 'DID NOT ANSWER IN TIME'",
            Integer.class,
            NPI);
    assertThat(notAnswered).isEqualTo(4);
  }
}
