package gov.medicaid.pem.sanctions.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.sanctions.ReviewFixesTest;
import gov.medicaid.pem.testsupport.PemJwt;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * MAM-52 code review 2026-11-19, MUST-FIX 3 (D-024): the call to provider-service has a timeout,
 * {@code pem.services.provider-timeout} (one second in these tests). provider-service takes three
 * seconds to answer the status change, so the online POST /api/v1/sanctions answers 503
 * problem+json that names the NPI (AC-7: a failure to apply is never reported as applied) and
 * records and announces nothing, instead of hanging until the answer comes.
 *
 * <p>Time limits: the POST itself runs under a preemptive limit of 15 seconds (its thread is given
 * up when it does not answer), and every test of the class is limited to 30 seconds by JUnit.
 * Without the timeout in the service a test therefore fails (the POST answers 201 after the three
 * seconds), it never hangs.
 */
@Timeout(30)
class ProviderTimeoutOnlineTest extends ReviewFixesTest {

  static final int SLOW_MILLIS = 3000;
  static final Duration CALL_BOUND = Duration.ofSeconds(15);

  @BeforeEach
  void providerHangs() {
    providerAnswersSlowly(NPI_1, SLOW_MILLIS);
  }

  MvcResult terminate() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("npi", NPI_1);
    body.put("action", "T");
    body.put("reason", REASON);
    body.put("effectiveDate", BUSINESS_DATE);
    return assertTimeoutPreemptively(
        CALL_BOUND,
        () ->
            mvc.perform(
                    post("/api/v1/sanctions")
                        .with(PemJwt.supervisor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andReturn(),
        "POST /api/v1/sanctions did not answer within "
            + CALL_BOUND
            + ": provider-service has no timeout");
  }

  @Test
  @DisplayName(
      "[MAM-52 AC-7] a status change provider-service does not answer in time is 503 problem+json, not 201 (code review 2026-11-19)")
  void timeoutIs503() {
    MvcResult r = terminate();
    assertThat(r.getResponse().getStatus()).isEqualTo(503);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
  }

  @Test
  @DisplayName(
      "[MAM-52 AC-7] the 503 of a status change that timed out names the NPI (code review 2026-11-19)")
  void timeoutNamesTheNpi() throws Exception {
    JsonNode problem = json.readTree(terminate().getResponse().getContentAsString());
    assertThat(problem.path("status").asInt()).isEqualTo(503);
    assertThat(problem.path("detail").asText()).contains(NPI_1);
  }

  @Test
  @DisplayName(
      "[MAM-52 AC-7] a status change that timed out is not recorded as applied and no SanctionApplied is published (code review 2026-11-19)")
  void timeoutRecordsNothing() {
    terminate();
    assertThat(historyRows(NPI_1)).isZero();
    assertThat(sanctionAppliedEvents(NPI_1)).isZero();
  }
}
