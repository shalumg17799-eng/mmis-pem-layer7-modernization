package gov.medicaid.pem.sanctions.infrastructure;

import gov.medicaid.pem.sanctions.application.SanctionHistory;
import gov.medicaid.pem.sanctions.application.SanctionPage;
import gov.medicaid.pem.sanctions.domain.Sanction;
import gov.medicaid.pem.sanctions.domain.SanctionAction;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** The sanction table of sanctions_db (Flyway V1); created_at holds the recordedAt time. */
@Repository
public class JdbcSanctionHistory implements SanctionHistory {

  private final JdbcTemplate jdbc;

  public JdbcSanctionHistory(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void add(Sanction s) {
    Timestamp at = Timestamp.from(s.recordedAt());
    jdbc.update(
        "INSERT INTO sanction (public_id, npi, action, reason, effective_date, source_feed, recorded_by,"
            + " resulting_status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        s.sanctionId(),
        s.npi(),
        s.action().name(),
        s.reason(),
        Date.valueOf(s.effectiveDate()),
        s.sourceFeed(),
        s.recordedBy(),
        s.resultingStatus(),
        at,
        at);
  }

  @Override
  public SanctionPage find(String npi, int page, int size) {
    String where = npi == null ? "" : " WHERE npi = ?";
    List<Object> args = new ArrayList<>();
    if (npi != null) {
      args.add(npi);
    }
    Long total =
        jdbc.queryForObject("SELECT count(*) FROM sanction" + where, Long.class, args.toArray());
    List<Object> pageArgs = new ArrayList<>(args);
    pageArgs.add(size);
    pageArgs.add((long) page * size);
    List<Sanction> items =
        jdbc.query(
            "SELECT public_id, npi, action, reason, effective_date, source_feed, recorded_by, created_at"
                + " FROM sanction"
                + where
                + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
            JdbcSanctionHistory::row,
            pageArgs.toArray());
    return new SanctionPage(items, page, size, total == null ? 0 : total);
  }

  private static Sanction row(ResultSet rs, int n) throws SQLException {
    return new Sanction(
        rs.getObject("public_id", UUID.class),
        rs.getString("npi").trim(),
        SanctionAction.fromCode(rs.getString("action").trim()).orElseThrow(),
        rs.getString("reason"),
        rs.getDate("effective_date").toLocalDate(),
        rs.getString("source_feed"),
        rs.getString("recorded_by"),
        rs.getTimestamp("created_at").toInstant());
  }
}
