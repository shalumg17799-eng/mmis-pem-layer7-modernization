package gov.medicaid.pem.revalidation.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.kit.events.EventEnvelope;
import gov.medicaid.pem.kit.events.EventJson;
import gov.medicaid.pem.kit.events.ProcessedEvents;
import gov.medicaid.pem.revalidation.application.CompleteCase;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes pem.screening.events: a ScreeningCompleted with reason REVALIDATION completes the
 * provider's open case (MAM-48 AC-4, AC-5); every other reason is ignored (AC-6). Applied once.
 */
@Component
public class ScreeningEventsListener {

  private final ObjectMapper json;
  private final ProcessedEvents processed;
  private final CompleteCase complete;

  public ScreeningEventsListener(
      ObjectMapper json, ProcessedEvents processed, CompleteCase complete) {
    this.json = json;
    this.processed = processed;
    this.complete = complete;
  }

  @KafkaListener(topics = "pem.screening.events")
  @Transactional
  public void onEvent(String message) {
    EventEnvelope e = EventJson.parse(json, message);
    if (!processed.firstTime(e.id())) {
      return;
    }
    if ("ScreeningCompleted".equals(e.type())) {
      JsonNode d = e.data();
      JsonNode screening = d.path("screeningId");
      complete.byScreening(
          d.path("npi").asText(e.subject()),
          d.path("reason").asText(),
          d.path("result").asText(),
          screening.isTextual() ? UUID.fromString(screening.asText()) : null);
    }
  }
}
