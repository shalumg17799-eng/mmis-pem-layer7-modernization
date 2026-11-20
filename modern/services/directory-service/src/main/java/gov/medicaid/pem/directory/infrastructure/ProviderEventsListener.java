package gov.medicaid.pem.directory.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.directory.application.ProjectDirectory;
import gov.medicaid.pem.kit.events.EventEnvelope;
import gov.medicaid.pem.kit.events.EventJson;
import gov.medicaid.pem.kit.events.ProcessedEvents;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes pem.provider.events (ProviderRegistered, ProviderStatusChanged) and pem.risk.events
 * (RiskTierAssigned); each event id is applied once (processed_event, ADR-003).
 */
@Component
public class ProviderEventsListener {

  private final ObjectMapper json;
  private final ProcessedEvents processed;
  private final ProjectDirectory project;

  public ProviderEventsListener(
      ObjectMapper json, ProcessedEvents processed, ProjectDirectory project) {
    this.json = json;
    this.processed = processed;
    this.project = project;
  }

  @KafkaListener(topics = {"pem.provider.events", "pem.risk.events"})
  @Transactional
  public void onEvent(String message) {
    EventEnvelope e = EventJson.parse(json, message);
    if (!processed.firstTime(e.id())) {
      return;
    }
    JsonNode d = e.data();
    switch (e.type()) {
      case "ProviderRegistered" ->
          project.registered(
              e.subject(),
              d.path("medicaidId").asText(null),
              d.path("providerType").asText(),
              d.path("taxonomy").asText(null),
              d.path("displayName").asText(""));
      case "ProviderStatusChanged" -> project.statusChanged(e.subject(), d.path("to").asText());
      case "RiskTierAssigned" -> project.tierAssigned(e.subject(), d.path("tier").asText());
      default -> {
        // other provider and risk events do not change the directory
      }
    }
  }
}
