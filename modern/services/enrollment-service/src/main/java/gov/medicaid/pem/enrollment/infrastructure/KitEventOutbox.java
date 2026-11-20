package gov.medicaid.pem.enrollment.infrastructure;

import gov.medicaid.pem.enrollment.application.EventOutbox;
import gov.medicaid.pem.kit.events.OutboxWriter;
import gov.medicaid.pem.kit.web.CorrelationId;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The shared transactional outbox of service-kit (ADR-012); the relay publishes to
 * pem.enrollment.events. The correlationId of an event is a uuid (events.yaml): a request id that
 * is safe to log but no uuid (for example "trace-42") is not put into the event, a new uuid is.
 */
@Component
public class KitEventOutbox implements EventOutbox {

  private final OutboxWriter writer;

  public KitEventOutbox(OutboxWriter writer) {
    this.writer = writer;
  }

  @Override
  public void enqueue(String type, String subject, Object data) {
    CorrelationId.with(
        uuidOf(CorrelationId.currentOrNew()), () -> writer.enqueue(type, subject, data));
  }

  /** The id as a canonical uuid, or a new uuid when it is none. */
  private static String uuidOf(String id) {
    try {
      return UUID.fromString(id).toString();
    } catch (IllegalArgumentException e) {
      return UUID.randomUUID().toString();
    }
  }
}
