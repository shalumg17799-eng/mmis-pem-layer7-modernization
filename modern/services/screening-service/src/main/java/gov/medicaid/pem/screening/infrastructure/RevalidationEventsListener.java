package gov.medicaid.pem.screening.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.kit.events.EventEnvelope;
import gov.medicaid.pem.kit.events.EventJson;
import gov.medicaid.pem.kit.events.ProcessedEvents;
import gov.medicaid.pem.screening.application.ScreenProvider;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes pem.revalidation.events (MAM-28 AC-3, AC-4): a RevalidationDue starts one REVALIDATION
 * screening with the provider's current data; its ScreeningCompleted closes the case in
 * revalidation-service. Each event id is handled once (processed_event), so a redelivery starts no
 * second screening (D-029, ADR-003).
 */
@Component
public class RevalidationEventsListener {

  static final String REVALIDATION = "REVALIDATION";

  private final ObjectMapper json;
  private final ProcessedEvents processed;
  private final ScreenProvider screen;

  public RevalidationEventsListener(
      ObjectMapper json, ProcessedEvents processed, ScreenProvider screen) {
    this.json = json;
    this.processed = processed;
    this.screen = screen;
  }

  @KafkaListener(topics = "pem.revalidation.events")
  @Transactional
  public void onEvent(String message) {
    EventEnvelope e = EventJson.parse(json, message);
    if (!processed.firstTime(e.id())) {
      return;
    }
    if ("RevalidationDue".equals(e.type())) {
      screen.screen(e.data().path("npi").asText(e.subject()), REVALIDATION);
    }
  }
}
