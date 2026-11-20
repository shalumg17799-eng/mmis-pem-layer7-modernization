package gov.medicaid.pem.provider.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.kit.events.EventEnvelope;
import gov.medicaid.pem.kit.events.EventJson;
import gov.medicaid.pem.kit.events.ProcessedEvents;
import gov.medicaid.pem.provider.application.RecordNoticeSent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes pem.notification.events (events.yaml: NoticeSent is consumed by provider-service): each
 * notice sent is added once to the provider's history (MAM-40 AC-4); a redelivered event id is
 * ignored (processed_event).
 */
@Component
public class NoticeEventsListener {

  private final ObjectMapper json;
  private final ProcessedEvents processed;
  private final RecordNoticeSent record;

  public NoticeEventsListener(
      ObjectMapper json, ProcessedEvents processed, RecordNoticeSent record) {
    this.json = json;
    this.processed = processed;
    this.record = record;
  }

  @KafkaListener(topics = "pem.notification.events")
  @Transactional
  public void onEvent(String message) {
    EventEnvelope e = EventJson.parse(json, message);
    if (!processed.firstTime(e.id())) {
      return;
    }
    if ("NoticeSent".equals(e.type())) {
      record.noticeSent(
          e.subject(),
          e.data().path("noticeId").asText(),
          e.data().path("template").asText(),
          e.source() == null ? "notification-service" : e.source());
    }
  }
}
