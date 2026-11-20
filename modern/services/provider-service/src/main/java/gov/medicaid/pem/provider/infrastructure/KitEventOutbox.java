package gov.medicaid.pem.provider.infrastructure;

import gov.medicaid.pem.kit.events.OutboxWriter;
import gov.medicaid.pem.provider.application.EventOutbox;
import org.springframework.stereotype.Component;

/**
 * The shared transactional outbox of service-kit (ADR-012); the relay publishes to
 * pem.provider.events.
 */
@Component
public class KitEventOutbox implements EventOutbox {

  private final OutboxWriter writer;

  public KitEventOutbox(OutboxWriter writer) {
    this.writer = writer;
  }

  @Override
  public void enqueue(String type, String subject, Object data) {
    writer.enqueue(type, subject, data);
  }
}
