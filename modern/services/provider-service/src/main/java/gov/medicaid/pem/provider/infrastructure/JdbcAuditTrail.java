package gov.medicaid.pem.provider.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.provider.application.AuditEntry;
import gov.medicaid.pem.provider.application.AuditHistory;
import gov.medicaid.pem.provider.application.AuditTrail;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The provider history on provider_db.audit_log (MAM-40): rows are only ever inserted (Q-012);
 * before and after are stored as JSONB. Read oldest first, then by insertion order.
 */
@Component
public class JdbcAuditTrail implements AuditTrail, AuditHistory {

  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final Clock clock;

  public JdbcAuditTrail(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
    this.jdbc = jdbc;
    this.json = json;
    this.clock = clock;
  }

  @Override
  public void append(AuditEntry e) {
    jdbc.update(
        "INSERT INTO audit_log (npi, actor, action, before_json, after_json, correlation_id, at)"
            + " VALUES (?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?)",
        e.npi(),
        e.actor() == null ? "unknown" : e.actor(),
        e.action(),
        write(e.before()),
        write(e.after()),
        uuid(e.correlationId()),
        Timestamp.from(e.at() == null ? clock.instant() : e.at()));
  }

  @Override
  public Page of(String npi, int page, int size) {
    Long total =
        jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE npi = ?", Long.class, npi);
    var items =
        jdbc.query(
            "SELECT npi, at, actor, action, before_json::text AS b, after_json::text AS a,"
                + " correlation_id FROM audit_log WHERE npi = ? ORDER BY at, id LIMIT ? OFFSET ?",
            this::row,
            npi,
            size,
            (long) page * size);
    return new Page(items, total == null ? 0 : total);
  }

  private AuditEntry row(ResultSet rs, int n) throws SQLException {
    Object cid = rs.getObject("correlation_id");
    return new AuditEntry(
        rs.getString("npi").trim(),
        rs.getTimestamp("at").toInstant(),
        rs.getString("actor"),
        rs.getString("action"),
        read(rs.getString("b")),
        read(rs.getString("a")),
        cid == null ? null : cid.toString());
  }

  private String write(Map<String, Object> m) {
    if (m == null) {
      return null;
    }
    try {
      return json.writeValueAsString(m);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("audit entry cannot be written as JSON", e);
    }
  }

  private Map<String, Object> read(String s) {
    if (s == null) {
      return null;
    }
    try {
      return json.readValue(s, MAP);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("audit entry cannot be read", e);
    }
  }

  /** audit_log.correlation_id is a UUID; an id that is not one is kept out of the column. */
  private static UUID uuid(String s) {
    if (s == null) {
      return null;
    }
    try {
      return UUID.fromString(s);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
