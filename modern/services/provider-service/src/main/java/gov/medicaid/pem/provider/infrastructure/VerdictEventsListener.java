package gov.medicaid.pem.provider.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.kit.events.EventEnvelope;
import gov.medicaid.pem.kit.events.EventJson;
import gov.medicaid.pem.kit.events.ProcessedEvents;
import gov.medicaid.pem.provider.application.CompleteRevalidation;
import gov.medicaid.pem.provider.application.RecordVerdicts;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes pem.screening.events (ScreeningCompleted -> screen flag, MAM-21) and pem.risk.events
 * (RiskTierAssigned -> risk tier, MAM-23). Each event id is applied once (processed_event); the two
 * topics may arrive in any order.
 */
@Component
public class VerdictEventsListener {

  private final ObjectMapper json;
  private final ProcessedEvents processed;
  private final RecordVerdicts record;
  private final CompleteRevalidation revalidation;

  public VerdictEventsListener(
      ObjectMapper json,
      ProcessedEvents processed,
      RecordVerdicts record,
      CompleteRevalidation revalidation) {
    this.json = json;
    this.processed = processed;
    this.record = record;
    this.revalidation = revalidation;
  }

  @KafkaListener(topics = {"pem.screening.events", "pem.risk.events"})
  @Transactional
  public void onEvent(String message) {
    EventEnvelope e = EventJson.parse(json, message);
    if (!processed.firstTime(e.id())) {
      return;
    }
    if ("ScreeningCompleted".equals(e.type())) {
      String result = e.data().path("result").asText();
      boolean known =
          record.screeningCompleted(e.subject(), result, source(e, "screening-service"));
      // MAM-30: only a passed revalidation moves the due date (D-003, Q-A4)
      if (known
          && "PASS".equals(result)
          && "REVALIDATION".equals(e.data().path("reason").asText())) {
        revalidation.passed(e.subject());
      }
    } else if ("RiskTierAssigned".equals(e.type())) {
      record.riskTierAssigned(
          e.subject(), e.data().path("tier").asText(), source(e, "risk-service"));
    }
  }

  /** MAM-40 AC-3: the producing service named in the envelope is the actor of the change. */
  private static String source(EventEnvelope e, String fallback) {
    return e.source() == null || e.source().isBlank() ? fallback : e.source();
  }
}
