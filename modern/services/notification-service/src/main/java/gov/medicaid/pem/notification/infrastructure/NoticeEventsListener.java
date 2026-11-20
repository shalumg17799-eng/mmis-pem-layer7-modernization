package gov.medicaid.pem.notification.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.kit.events.EventEnvelope;
import gov.medicaid.pem.kit.events.EventJson;
import gov.medicaid.pem.kit.events.ProcessedEvents;
import gov.medicaid.pem.notification.application.SendNotice;
import gov.medicaid.pem.notification.application.SendSanctionNotice;
import gov.medicaid.pem.notification.domain.NoticeRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes the events that can lead to a notice (MAM-59). Each event id is handled once
 * (processed_event), so a redelivered event never sends a second email.
 */
@Component
public class NoticeEventsListener {

  private static final Logger LOG = LoggerFactory.getLogger(NoticeEventsListener.class);

  private final ObjectMapper json;
  private final ProcessedEvents processed;
  private final SendNotice notices;
  private final SendSanctionNotice sanctionNotices;

  public NoticeEventsListener(
      ObjectMapper json,
      ProcessedEvents processed,
      SendNotice notices,
      SendSanctionNotice sanctionNotices) {
    this.json = json;
    this.processed = processed;
    this.notices = notices;
    this.sanctionNotices = sanctionNotices;
  }

  @KafkaListener(
      topics = {
        "pem.provider.events",
        "pem.enrollment.events",
        "pem.revalidation.events",
        "pem.tmsis.events",
        "pem.sanctions.events"
      })
  @Transactional
  public void onEvent(String message) {
    EventEnvelope e = EventJson.parse(json, message);
    if (!processed.firstTime(e.id())) {
      return;
    }
    JsonNode d = e.data();
    switch (e.type()) {
      case "ProviderStatusChanged" ->
          NoticeRules.forStatusChange(d.path("from").asText(), d.path("to").asText())
              .ifPresent(t -> notices.send(e.subject(), t, e.id()));
      case "ApplicationRejected" -> {
        String npi = NoticeAdapters.text(d, "npi").orElse(null);
        NoticeRules.forRejection(npi)
            .ifPresentOrElse(
                t -> notices.send(npi, t, e.id()),
                () -> LOG.info("ApplicationRejected {} names no NPI: no notice sent", e.id()));
      }
      case "SanctionApplied" ->
          sanctionNotices.send(
              NoticeAdapters.text(d, "npi").orElse(e.subject()),
              NoticeAdapters.text(d, "action").orElse(null),
              e.id());
      case "RevalidationDue" ->
          notices.send(
              NoticeAdapters.text(d, "npi").orElse(e.subject()), "REVALIDATION_DUE", e.id());
      case "ExtractProduced" ->
          // keyed by the extract run id, not an NPI (events.yaml 1.1.0, Q-061 B); no recipient, so
          // no notice: EXTRACT_READY stays listed but is not sent (Q-A9 / Q-023 A)
          LOG.info(
              "ExtractProduced {} for extract run {}: no notice (no recipient, Q-A9)",
              e.id(),
              NoticeAdapters.text(d, "runId").orElse(e.subject()));
      default -> {
        // every other event: no notice
      }
    }
  }
}
