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
 * Consumes pem.provider.events (ADR-003): ProviderRegistered starts the INITIAL screening (D-005).
 * Every event id is recorded once in processed_event, so a redelivered registration never creates a
 * second screening.
 */
@Component
public class ProviderEventsListener {

  private final ObjectMapper json;
  private final ProcessedEvents processed;
  private final ScreenProvider screen;

  public ProviderEventsListener(
      ObjectMapper json, ProcessedEvents processed, ScreenProvider screen) {
    this.json = json;
    this.processed = processed;
    this.screen = screen;
  }

  @KafkaListener(topics = "pem.provider.events")
  @Transactional
  public void onEvent(String message) {
    EventEnvelope e = EventJson.parse(json, message);
    if (!processed.firstTime(e.id())) {
      return;
    }
    if ("ProviderRegistered".equals(e.type())) {
      screen.screen(e.subject(), "INITIAL");
    }
  }
}
