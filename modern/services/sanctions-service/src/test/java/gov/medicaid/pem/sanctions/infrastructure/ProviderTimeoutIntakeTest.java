package gov.medicaid.pem.sanctions.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.sanctions.ReviewFixesTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * MAM-55 code review 2026-11-19, MUST-FIX 3 (D-024): the call to provider-service has a timeout,
 * {@code pem.services.provider-timeout} (one second in these tests). provider-service takes three
 * seconds to answer the status change of record 2. The call is cut off, so the status change is
 * "not applied" like any other failure (AC-6): the run ends FAILED naming record 2 and its NPI
 * instead of waiting for the answer or staying RUNNING.
 *
 * <p>Time limits: every test of the class is limited to 30 seconds by JUnit, and the wait for the
 * run is limited to 20 seconds. Without the timeout in the service a test therefore fails (the run
 * completes after the three seconds, or is still RUNNING), it never hangs.
 */
@Timeout(30)
class ProviderTimeoutIntakeTest extends ReviewFixesTest {

  static final int SLOW_MILLIS = 3000;

  @BeforeEach
  void providerHangsOnRecordTwo() {
    providerAnswersSlowly(NPI_2, SLOW_MILLIS);
  }

  JsonNode run() throws Exception {
    return runToEndWithin(threeRecords(), RUN_BOUND);
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-6] a status change provider-service does not answer in time ends the run FAILED with endedAt set, not RUNNING and not COMPLETED (code review 2026-11-19)")
  void timeoutEndsTheRunFailed() throws Exception {
    JsonNode run = run();
    assertThat(run.path("status").asText()).isEqualTo("FAILED");
    assertThat(run.path("endedAt").asText()).isNotBlank();
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-6] the error of a run whose status change timed out names record 2 and its NPI (code review 2026-11-19)")
  void timeoutNamesTheRecord() throws Exception {
    JsonNode run = run();
    assertThat(run.path("error").asText()).contains("record 2").contains(NPI_2);
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-6] after the timeout record 2 is not recorded, record 1 keeps its result and record 3 is not processed (code review 2026-11-19)")
  void timeoutRecordsOnlyRecordOne() throws Exception {
    run();
    assertThat(jdbc.queryForList("SELECT npi FROM sanction ORDER BY npi", String.class))
        .containsExactly(NPI_1);
  }
}
