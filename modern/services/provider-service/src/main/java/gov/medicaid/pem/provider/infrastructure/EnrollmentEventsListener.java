package gov.medicaid.pem.provider.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.kit.events.EventEnvelope;
import gov.medicaid.pem.kit.events.EventJson;
import gov.medicaid.pem.kit.events.ProcessedEvents;
import gov.medicaid.pem.provider.application.RegisterProvider;
import gov.medicaid.pem.provider.domain.ProviderApplication;
import gov.medicaid.pem.provider.domain.ProviderType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes pem.enrollment.events (ADR-003). Every event id is recorded once in processed_event, so
 * a redelivery is ignored; only ApplicationValidated registers a provider, so a rejected
 * application never becomes one (D-001).
 */
@Component
public class EnrollmentEventsListener {

  private final ObjectMapper json;
  private final ProcessedEvents processed;
  private final RegisterProvider register;

  public EnrollmentEventsListener(
      ObjectMapper json, ProcessedEvents processed, RegisterProvider register) {
    this.json = json;
    this.processed = processed;
    this.register = register;
  }

  @KafkaListener(topics = "pem.enrollment.events")
  @Transactional
  public void onEvent(String message) {
    EventEnvelope e = EventJson.parse(json, message);
    if (!processed.firstTime(e.id())) {
      return;
    }
    if ("ApplicationValidated".equals(e.type())) {
      register.register(
          application(e.data()), e.source() == null ? "enrollment-service" : e.source());
    }
  }

  static ProviderApplication application(JsonNode d) {
    JsonNode i = d.path("individual");
    JsonNode o = d.path("organization");
    return new ProviderApplication(
        text(d, "npi"),
        text(d, "medicaidId"),
        ProviderType.fromCode(text(d, "providerType")),
        text(d, "taxonomy"),
        text(i, "lastName"),
        text(i, "firstName"),
        text(i, "licenseNo"),
        text(i, "licenseState"),
        text(o, "legalName"),
        text(o, "dbaName"),
        text(o, "ownerNpi"));
  }

  private static String text(JsonNode n, String field) {
    JsonNode v = n.path(field);
    return v.isMissingNode() || v.isNull() ? null : v.asText();
  }
}
