package gov.medicaid.pem.revalidation.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.kit.events.EventEnvelope;
import gov.medicaid.pem.kit.events.EventJson;
import gov.medicaid.pem.kit.events.ProcessedEvents;
import gov.medicaid.pem.revalidation.application.RequestScreeningStage;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads this service's own pem.revalidation.events: once a RevalidationDue has been published the
 * re-screening is requested, so its case moves from DUE to IN_SCREENING (MAM-49 AC-1, C-13).
 */
@Component
public class RevalidationEventsListener {

  private final ObjectMapper json;
  private final ProcessedEvents processed;
  private final RequestScreeningStage stage;

  public RevalidationEventsListener(
      ObjectMapper json, ProcessedEvents processed, RequestScreeningStage stage) {
    this.json = json;
    this.processed = processed;
    this.stage = stage;
  }

  @KafkaListener(topics = "pem.revalidation.events")
  @Transactional
  public void onEvent(String message) {
    EventEnvelope e = EventJson.parse(json, message);
    if (!processed.firstTime(e.id())) {
      return;
    }
    if ("RevalidationDue".equals(e.type())) {
      JsonNode caseId = e.data().path("caseId");
      if (caseId.isTextual()) {
        stage.revalidationDuePublished(UUID.fromString(caseId.asText()));
      }
    }
  }
}
