package gov.medicaid.pem.kit.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.kit.web.CorrelationId;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Transactional outbox (ADR-003): the event is written to {@code outbox_event} in the same database
 * transaction as the state change; {@link OutboxRelay} publishes it to Kafka afterwards.
 */
public class OutboxWriter {

  static final String INSERT_SQL =
      "INSERT INTO outbox_event (id, type, subject, payload, created_at) VALUES (?, ?, ?, CAST(? AS jsonb), ?)";

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final Clock clock;
  private final String source;

  public OutboxWriter(JdbcTemplate jdbc, ObjectMapper json, Clock clock, String source) {
    this.jdbc = jdbc;
    this.json = json;
    this.clock = clock;
    this.source = source;
  }

  /**
   * Queues an event; call it inside the transaction that changes the state. Returns the event id.
   */
  public UUID enqueue(String type, String subject, Object data) {
    UUID id = UUID.randomUUID();
    Instant now = clock.instant();
    var envelope =
        new EventEnvelope(
            id, type, source, now, subject, CorrelationId.currentOrNew(), json.valueToTree(data));
    try {
      jdbc.update(
          INSERT_SQL, id, type, subject, json.writeValueAsString(envelope), Timestamp.from(now));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("cannot serialise event " + type, e);
    }
    return id;
  }
}
