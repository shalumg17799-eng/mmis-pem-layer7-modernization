package gov.medicaid.pem.revalidation.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.kit.events.EventEnvelope;
import gov.medicaid.pem.kit.events.EventJson;
import gov.medicaid.pem.kit.events.ProcessedEvents;
import gov.medicaid.pem.revalidation.application.FollowProvider;
import gov.medicaid.pem.revalidation.domain.TrackedProvider;
import java.time.LocalDate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes pem.provider.events: every ProviderStatusChanged sets the tracked status and due date of
 * the provider (the read model of D-014 / overview difference 4, MAM-45) and a provider that is no
 * longer ACTIVE cancels its open case (MAM-49). Each event id is applied once.
 */
@Component
public class ProviderEventsListener {

  private final ObjectMapper json;
  private final ProcessedEvents processed;
  private final FollowProvider follow;

  public ProviderEventsListener(
      ObjectMapper json, ProcessedEvents processed, FollowProvider follow) {
    this.json = json;
    this.processed = processed;
    this.follow = follow;
  }

  @KafkaListener(topics = "pem.provider.events")
  @Transactional
  public void onEvent(String message) {
    EventEnvelope e = EventJson.parse(json, message);
    if (!processed.firstTime(e.id())) {
      return;
    }
    if ("ProviderStatusChanged".equals(e.type())) {
      JsonNode d = e.data();
      JsonNode due = d.path("revalidationDueDate");
      follow.statusChanged(
          new TrackedProvider(
              d.path("npi").asText(e.subject()),
              d.path("to").asText(),
              due.isTextual() ? LocalDate.parse(due.asText()) : null));
    }
  }
}
