package gov.medicaid.pem.sanctions.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.sanctions.EventsContractFile;
import gov.medicaid.pem.sanctions.IntakeApi;
import gov.medicaid.pem.sanctions.ReviewFixesTest;
import gov.medicaid.pem.sanctions.SancinFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MAM-55 code review 2026-11-19, SHOULD-FIX 4: the SanctionApplied event of a SANCIN record whose
 * reason (bytes 12-51) is blank is the contract's SanctionAppliedEnvelope (modern/contracts/
 * asyncapi/events.yaml, 1.1.0): {@code SanctionAppliedData.reason} is a non-nullable string, so the
 * member is omitted, never {@code "reason": null}. The envelope is the one stored in the outbox.
 */
class SanctionIntakeEventContractTest extends ReviewFixesTest {

  /** Runs a file of one record, T for NPI_1, with a blank reason; the run completes. */
  JsonNode eventOfBlankReasonRecord() throws Exception {
    byte[] file = SancinFile.lines(SancinFile.record(NPI_1, "T", "", "20260331"));
    JsonNode run = IntakeApi.runToEnd(mvc, json, file, BUSINESS_DATE);
    assertThat(run.path("status").asText())
        .as("the run, error: " + run.path("error"))
        .isEqualTo("COMPLETED");
    return json.readTree(
        jdbc.queryForObject(
            "SELECT payload::text FROM outbox_event WHERE type = 'SanctionApplied' AND subject = ?",
            String.class,
            NPI_1));
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-3] the SanctionApplied event of a record with a blank reason has no reason member, not null (code review 2026-11-19)")
  void blankReasonIsOmitted() throws Exception {
    assertThat(eventOfBlankReasonRecord().path("data").has("reason")).isFalse();
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-3] the SanctionApplied event of a record with a blank reason is a valid SanctionAppliedEnvelope of events.yaml (code review 2026-11-19)")
  void eventOfBlankReasonConformsToTheContract() throws Exception {
    assertThat(EventsContractFile.violations("SanctionAppliedEnvelope", eventOfBlankReasonRecord()))
        .isEmpty();
  }
}
