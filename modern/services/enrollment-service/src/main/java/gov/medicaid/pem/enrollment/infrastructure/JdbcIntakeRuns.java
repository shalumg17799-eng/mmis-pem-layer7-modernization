package gov.medicaid.pem.enrollment.infrastructure;

import gov.medicaid.pem.enrollment.application.IntakeRuns;
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
 * intake_run (V1) and application.intake_run_id / raw_record: counts and APPERROR rows are written
 * after every record, so a failed run shows what was done (MAM-57 AC-5, AC-6).
 */
@Repository
public class JdbcIntakeRuns implements IntakeRuns {

  static final int ERROR_MAX = 200;

  private final JdbcTemplate jdbc;

  public JdbcIntakeRuns(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void create(UUID runId, LocalDate businessDate, String batchId, Instant startedAt) {
    jdbc.update(
        "INSERT INTO intake_run (public_id, business_date, batch_id, status, started_at)"
            + " VALUES (?, ?, ?, 'RUNNING', ?)",
        runId,
        businessDate,
        batchId,
        Timestamp.from(startedAt));
  }

  @Override
  public void counts(UUID runId, int read, int written, int errored, int skipped) {
    jdbc.update(
        "UPDATE intake_run SET read_count = ?, written_count = ?, error_count = ?, skipped_count = ?,"
            + " updated_at = now() WHERE public_id = ?",
        read,
        written,
        errored,
        skipped,
        runId);
  }

  @Override
  public void addErrorRow(UUID runId, String row) {
    jdbc.update(
        "UPDATE intake_run SET error_file = coalesce(error_file, '') || ?, updated_at = now()"
            + " WHERE public_id = ?",
        row,
        runId);
  }

  @Override
  public void attach(UUID applicationId, UUID runId, String rawRecord) {
    jdbc.update(
        "UPDATE application SET intake_run_id = (SELECT id FROM intake_run WHERE public_id = ?),"
            + " raw_record = ? WHERE public_id = ?",
        runId,
        rawRecord,
        applicationId);
  }

  @Override
  public boolean alreadyProcessed(UUID runId, LocalDate businessDate, String rawRecord) {
    Integer n =
        jdbc.queryForObject(
            "SELECT count(*) FROM application a JOIN intake_run r ON r.id = a.intake_run_id"
                + " WHERE r.business_date = ? AND r.public_id <> ? AND a.raw_record = ?",
            Integer.class,
            businessDate,
            runId,
            rawRecord);
    return n != null && n > 0;
  }

  @Override
  public void complete(UUID runId, Instant endedAt) {
    jdbc.update(
        "UPDATE intake_run SET status = 'COMPLETED', ended_at = ?, updated_at = ?,"
            + " version = version + 1 WHERE public_id = ?",
        Timestamp.from(endedAt),
        Timestamp.from(endedAt),
        runId);
  }

  @Override
  public void fail(UUID runId, String error, Instant endedAt) {
    String reason = error == null || error.isBlank() ? "failed" : error;
    jdbc.update(
        "UPDATE intake_run SET status = 'FAILED', failure_reason = ?, ended_at = ?, updated_at = ?,"
            + " version = version + 1 WHERE public_id = ? AND status = 'RUNNING'",
        reason.length() <= ERROR_MAX ? reason : reason.substring(0, ERROR_MAX),
        Timestamp.from(endedAt),
        Timestamp.from(endedAt),
        runId);
  }

  @Override
  public Optional<Run> find(UUID runId) {
    return jdbc
        .query(
            "SELECT public_id, business_date, batch_id, status, read_count, written_count,"
                + " error_count, skipped_count, failure_reason, started_at, ended_at"
                + " FROM intake_run WHERE public_id = ?",
            JdbcIntakeRuns::run,
            runId)
        .stream()
        .findFirst();
  }

  @Override
  public Optional<String> errorFile(UUID runId) {
    return jdbc
        .query(
            "SELECT coalesce(error_file, '') FROM intake_run WHERE public_id = ?",
            (rs, n) -> rs.getString(1),
            runId)
        .stream()
        .findFirst();
  }

  private static Run run(ResultSet rs, int n) throws SQLException {
    Timestamp ended = rs.getTimestamp("ended_at");
    return new Run(
        rs.getObject("public_id", UUID.class),
        rs.getObject("business_date", LocalDate.class),
        rs.getString("batch_id"),
        rs.getString("status"),
        rs.getInt("read_count"),
        rs.getInt("written_count"),
        rs.getInt("error_count"),
        rs.getInt("skipped_count"),
        rs.getString("failure_reason"),
        rs.getTimestamp("started_at").toInstant(),
        ended == null ? null : ended.toInstant());
  }
}
