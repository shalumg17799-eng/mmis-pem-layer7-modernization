package gov.medicaid.pem.risk.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.kit.events.EventEnvelope;
import gov.medicaid.pem.kit.events.EventJson;
import gov.medicaid.pem.kit.events.ProcessedEvents;
import gov.medicaid.pem.kit.web.CorrelationId;
import gov.medicaid.pem.risk.application.AssessRisk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes pem.screening.events (ADR-003). Each event id is applied once (processed_event). A
 * failure is logged with the correlation id and rethrown, so the transaction rolls back (nothing
 * published) and the kit's error handler retries it and then parks it on the dead-letter topic
 * (D-024); a later redelivery is applied normally.
 */
@Component
public class ScreeningEventsListener {

  private static final Logger LOG = LoggerFactory.getLogger(ScreeningEventsListener.class);

  private final ObjectMapper json;
  private final ProcessedEvents processed;
  private final AssessRisk assess;

  public ScreeningEventsListener(ObjectMapper json, ProcessedEvents processed, AssessRisk assess) {
    this.json = json;
    this.processed = processed;
    this.assess = assess;
  }

  @KafkaListener(topics = "pem.screening.events")
  @Transactional
  public void onEvent(String message) {
    EventEnvelope e = EventJson.parse(json, message);
    try {
      if (!processed.firstTime(e.id())) {
        return;
      }
      if ("ScreeningCompleted".equals(e.type())) {
        assess.screeningCompleted(
            e.subject(), e.data().path("result").asText(), e.data().path("taxonomy").asText());
      }
    } catch (RuntimeException ex) {
      LOG.error(
          "risk assessment failed for event {} of NPI {} (correlation id {}): {}",
          e.id(),
          e.subject(),
          MDC.get(CorrelationId.MDC_KEY),
          ex.toString());
      throw ex;
    }
  }
}
