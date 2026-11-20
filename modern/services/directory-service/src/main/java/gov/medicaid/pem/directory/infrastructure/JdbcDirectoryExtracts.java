package gov.medicaid.pem.directory.infrastructure;

import gov.medicaid.pem.directory.application.DirectoryExtracts;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** directory_db.extract_run (V1): one row per directory extract run, with its DIROUT file. */
@Repository
public class JdbcDirectoryExtracts implements DirectoryExtracts {

  static final int ERROR_MAX = 500;

  private final JdbcTemplate jdbc;

  public JdbcDirectoryExtracts(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void create(Run r) {
    jdbc.update(
        "INSERT INTO extract_run (public_id, business_date, status, row_count, started_at)"
            + " VALUES (?, ?, ?, 0, ?)",
        r.runId(),
        r.businessDate(),
        r.status(),
        Timestamp.from(r.startedAt()));
  }

  @Override
  public void complete(UUID runId, int rows, byte[] file, Instant endedAt) {
    jdbc.update(
        "UPDATE extract_run SET status = 'COMPLETED', row_count = ?, extract_file = ?, ended_at = ?,"
            + " updated_at = ?, version = version + 1 WHERE public_id = ?",
        rows,
        new String(file, StandardCharsets.US_ASCII),
        Timestamp.from(endedAt),
        Timestamp.from(endedAt),
        runId);
  }

  @Override
  public void fail(UUID runId, String error, Instant endedAt) {
    String reason = error == null || error.isBlank() ? "failed" : error;
    jdbc.update(
        "UPDATE extract_run SET status = 'FAILED', failure_reason = ?, extract_file = NULL,"
            + " ended_at = ?, updated_at = ?, version = version + 1"
            + " WHERE public_id = ? AND status = 'RUNNING'",
        reason.length() <= ERROR_MAX ? reason : reason.substring(0, ERROR_MAX),
        Timestamp.from(endedAt),
        Timestamp.from(endedAt),
        runId);
  }

  @Override
  public Optional<Run> find(UUID runId) {
    return jdbc
        .query(
            "SELECT public_id, business_date, status, row_count, failure_reason, started_at,"
                + " ended_at FROM extract_run WHERE public_id = ?",
            JdbcDirectoryExtracts::run,
            runId)
        .stream()
        .findFirst();
  }

  @Override
  public Optional<byte[]> file(UUID runId) {
    return jdbc
        .query(
            "SELECT extract_file FROM extract_run WHERE public_id = ? AND status = 'COMPLETED'"
                + " AND extract_file IS NOT NULL",
            (rs, n) -> rs.getString(1).getBytes(StandardCharsets.US_ASCII),
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
        rs.getInt("row_count"),
        rs.getString("failure_reason"),
        rs.getTimestamp("started_at").toInstant(),
        ended == null ? null : ended.toInstant());
  }
}
