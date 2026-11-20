package gov.medicaid.pem.kit.events;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Idempotent consumers (ADR-003): records each processed event id; a redelivered id is skipped. */
public class ProcessedEvents {

  static final String INSERT_SQL =
      "INSERT INTO processed_event (event_id) VALUES (?) ON CONFLICT DO NOTHING";

  private final JdbcTemplate jdbc;

  public ProcessedEvents(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** True the first time an event id is seen; call it inside the consumer's transaction. */
  public boolean firstTime(UUID eventId) {
    return jdbc.update(INSERT_SQL, eventId) == 1;
  }
}
