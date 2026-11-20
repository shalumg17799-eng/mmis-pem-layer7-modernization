package gov.medicaid.pem.sanctions.infrastructure;

import gov.medicaid.pem.sanctions.application.SanctionIntakeRuns;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * sanction_intake_run (V1) and sanction.intake_run_id. The terminated count is the run's applied T
 * sanctions (WS-TERM-COUNT); counts are written after every record.
 */
@Repository
public class JdbcSanctionIntakeRuns implements SanctionIntakeRuns {

  static final int ERROR_MAX = 500;

  private final JdbcTemplate jdbc;

  public JdbcSanctionIntakeRuns(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void create(UUID runId, LocalDate businessDate, Instant startedAt) {
    jdbc.update(
        "INSERT INTO sanction_intake_run (public_id, business_date, status, started_at)"
            + " VALUES (?, ?, 'RUNNING', ?)",
        runId,
        businessDate,
        Timestamp.from(startedAt));
  }

  @Override
  public void counts(UUID runId, int read, int applied, int notFound, int rejected) {
    jdbc.update(
        "UPDATE sanction_intake_run SET read_count = ?, applied_count = ?, not_found_count = ?,"
            + " rejected_count = ?, updated_at = now() WHERE public_id = ?",
        read,
        applied,
        notFound,
        rejected,
        runId);
  }

  @Override
  public void link(UUID sanctionId, UUID runId) {
    jdbc.update(
        "UPDATE sanction SET intake_run_id = (SELECT id FROM sanction_intake_run WHERE public_id = ?)"
            + " WHERE public_id = ?",
        runId,
        sanctionId);
  }

  @Override
  public boolean alreadyApplied(
      UUID runId,
      LocalDate businessDate,
      String npi,
      String action,
      String reason,
      LocalDate date) {
    Integer n =
        jdbc.queryForObject(
            "SELECT count(*) FROM sanction s JOIN sanction_intake_run r ON r.id = s.intake_run_id"
                + " WHERE r.business_date = ? AND r.public_id <> ? AND s.npi = ? AND s.action = ?"
                + " AND coalesce(s.reason, '') = ? AND s.effective_date = ?",
            Integer.class,
            businessDate,
            runId,
            npi,
            action,
            reason == null ? "" : reason,
            date);
    return n != null && n > 0;
  }

  @Override
  public void complete(UUID runId, Instant endedAt) {
    jdbc.update(
        "UPDATE sanction_intake_run SET status = 'COMPLETED', ended_at = ?, updated_at = ?,"
            + " version = version + 1 WHERE public_id = ?",
        Timestamp.from(endedAt),
        Timestamp.from(endedAt),
        runId);
  }

  @Override
  public void fail(UUID runId, String error, Instant endedAt) {
    String reason = error == null || error.isBlank() ? "failed" : error;
    jdbc.update(
        "UPDATE sanction_intake_run SET status = 'FAILED', failure_reason = ?, ended_at = ?,"
            + " updated_at = ?, version = version + 1 WHERE public_id = ? AND status = 'RUNNING'",
        reason.length() <= ERROR_MAX ? reason : reason.substring(0, ERROR_MAX),
        Timestamp.from(endedAt),
        Timestamp.from(endedAt),
        runId);
  }

  @Override
  public Optional<Run> find(UUID runId) {
    return jdbc
        .query(
            "SELECT r.public_id, r.business_date, r.status, r.read_count, r.applied_count,"
                + " r.not_found_count, r.rejected_count, r.failure_reason, r.started_at, r.ended_at,"
                + " (SELECT count(*) FROM sanction s WHERE s.intake_run_id = r.id AND s.action = 'T')"
                + " AS terminated FROM sanction_intake_run r WHERE r.public_id = ?",
            JdbcSanctionIntakeRuns::run,
            runId)
        .stream()
        .findFirst();
  }

  private static Run run(ResultSet rs, int n) throws SQLException {
    Timestamp ended = rs.getTimestamp("ended_at");
    return new Run(
        rs.getObject("public_id", UUID.class),
        rs.getObject("business_date", LocalDate.class),
        rs.getString("status"),
        rs.getInt("read_count"),
        rs.getInt("applied_count"),
        rs.getInt("not_found_count"),
        rs.getInt("rejected_count"),
        rs.getInt("terminated"),
        rs.getString("failure_reason"),
        rs.getTimestamp("started_at").toInstant(),
        ended == null ? null : ended.toInstant());
  }
}
