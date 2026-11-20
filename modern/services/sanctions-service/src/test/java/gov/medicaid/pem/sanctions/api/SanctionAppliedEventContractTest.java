package gov.medicaid.pem.sanctions.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.sanctions.EventsContractFile;
import gov.medicaid.pem.sanctions.ReviewFixesTest;
import gov.medicaid.pem.testsupport.PemJwt;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * MAM-52 code review 2026-11-19, SHOULD-FIX 4: the SanctionApplied event published for a sanction
 * recorded online is the contract's SanctionAppliedEnvelope (modern/contracts/asyncapi/events.yaml,
 * 1.1.0). {@code SanctionAppliedData.reason} is a non-nullable string and not required, so a
 * sanction without a reason is announced without a reason member, never with {@code "reason":
 * null}. The envelope is the one stored in the outbox, the text the relay publishes.
 */
class SanctionAppliedEventContractTest extends ReviewFixesTest {

  /** Records a termination of NPI_1 online as SUPERVISOR, with a reason or (null) without one. */
  void record(String reason) throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("npi", NPI_1);
    body.put("action", "T");
    if (reason != null) {
      body.put("reason", reason);
    }
    body.put("effectiveDate", BUSINESS_DATE);
    MvcResult r =
        mvc.perform(
                post("/api/v1/sanctions")
                    .with(PemJwt.supervisor())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body)))
            .andReturn();
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
  }

  /** The envelope of the one SanctionApplied event of NPI_1, as stored for the relay. */
  JsonNode publishedEvent() throws Exception {
    return json.readTree(
        jdbc.queryForObject(
            "SELECT payload::text FROM outbox_event WHERE type = 'SanctionApplied' AND subject = ?",
            String.class,
            NPI_1));
  }

  @Test
  @DisplayName(
      "[MAM-52 AC-1] the SanctionApplied event of a sanction without a reason has no reason member, not null (code review 2026-11-19)")
  void absentReasonIsOmitted() throws Exception {
    record(null);
    assertThat(publishedEvent().path("data").has("reason")).isFalse();
  }

  @Test
  @DisplayName(
      "[MAM-52 AC-1] the SanctionApplied event of a sanction without a reason is a valid SanctionAppliedEnvelope of events.yaml (code review 2026-11-19)")
  void eventWithoutReasonConformsToTheContract() throws Exception {
    record(null);
    assertThat(EventsContractFile.violations("SanctionAppliedEnvelope", publishedEvent()))
        .isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-52 AC-1] the SanctionApplied event of a sanction with a reason is a valid SanctionAppliedEnvelope of events.yaml and carries the reason (code review 2026-11-19)")
  void eventWithReasonConformsToTheContract() throws Exception {
    record(REASON);
    JsonNode event = publishedEvent();
    assertThat(EventsContractFile.violations("SanctionAppliedEnvelope", event)).isEmpty();
    assertThat(event.path("data").path("reason").asText()).isEqualTo(REASON);
  }
}
