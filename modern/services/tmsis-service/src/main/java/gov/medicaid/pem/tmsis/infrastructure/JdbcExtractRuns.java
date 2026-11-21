package gov.medicaid.pem.tmsis.infrastructure;

import gov.medicaid.pem.tmsis.application.ExtractRun;
import gov.medicaid.pem.tmsis.application.ExtractRuns;
import gov.medicaid.pem.tmsis.domain.TmsisRecord;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * extract_run, extract_row and extract_archive on tmsis_db. Archive generations are only ever added
 * (Q-012: nothing is overwritten or deleted); the next generation is taken under a table lock.
 */
@Component
public class JdbcExtractRuns implements ExtractRuns {

  static final int ERROR_MAX = 500;

  private static final String RUN_COLUMNS =
      "SELECT r.public_id, r.business_date, r.period_yyddd, r.status, r.row_count,"
          + " r.future_enrolment_count, a.generation,"
          + " r.failure_reason, r.started_at, r.ended_at FROM extract_run r"
          + " LEFT JOIN extract_archive a ON a.extract_run_id = r.id";

  private final JdbcTemplate jdbc;

  public JdbcExtractRuns(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void create(ExtractRun r) {
    jdbc.update(
        "INSERT INTO extract_run (public_id, business_date, period_yyddd, status, row_count,"
            + " started_at) VALUES (?, ?, ?, ?, 0, ?)",
        r.runId(),
        r.businessDate(),
        r.periodYyddd(),
        r.status(),
        Timestamp.from(r.startedAt()));
  }

  @Override
  public void addRows(UUID runId, List<TmsisRecord> rows) {
    if (rows.isEmpty()) {
      return;
    }
    Long id = runKey(runId);
    jdbc.batchUpdate(
        "INSERT INTO extract_row (extract_run_id, medicaid_id, npi, status, period_yyddd)"
            + " VALUES (?, ?, ?, ?, ?)",
        rows,
        rows.size(),
        (ps, t) -> {
          ps.setLong(1, id);
          ps.setString(2, t.medicaidId());
          ps.setString(3, t.npi());
          ps.setString(4, t.status());
          ps.setString(5, t.periodYyddd());
        });
  }

  @Override
  public void addFutureEnrolments(UUID runId, int count) {
    if (count > 0) {
      jdbc.update(
          "UPDATE extract_run SET future_enrolment_count = future_enrolment_count + ?"
              + " WHERE public_id = ?",
          count,
          runId);
    }
  }

  @Override
  public List<TmsisRecord> rows(UUID runId) {
    return jdbc.query(
        "SELECT medicaid_id, npi, status, period_yyddd FROM extract_row WHERE extract_run_id ="
            + " (SELECT id FROM extract_run WHERE public_id = ?) ORDER BY npi",
        (rs, n) ->
            new TmsisRecord(
                rs.getString("medicaid_id").trim(),
                rs.getString("npi").trim(),
                rs.getString("status"),
                rs.getString("period_yyddd")),
        runId);
  }

  @Override
  @Transactional
  public int complete(UUID runId, int rows, byte[] file, Instant at) {
    jdbc.execute("LOCK TABLE extract_archive IN EXCLUSIVE MODE");
    Integer last =
        jdbc.queryForObject("SELECT max(generation) FROM extract_archive", Integer.class);
    int generation = last == null ? 1 : last + 1;
    jdbc.update(
        "INSERT INTO extract_archive (extract_run_id, generation, content, archived_at)"
            + " VALUES (?, ?, ?, ?)",
        runKey(runId),
        generation,
        new String(file, StandardCharsets.US_ASCII),
        Timestamp.from(at));
    jdbc.update(
        "UPDATE extract_run SET status = 'COMPLETED', row_count = ?, ended_at = ?, updated_at = ?,"
            + " version = version + 1 WHERE public_id = ?",
        rows,
        Timestamp.from(at),
        Timestamp.from(at),
        runId);
    return generation;
  }

  @Override
  public void fail(UUID runId, String error, Instant at) {
    String reason = error == null || error.isBlank() ? "failed" : error;
    jdbc.update(
        "UPDATE extract_run SET status = 'FAILED', failure_reason = ?, ended_at = ?,"
            + " updated_at = ?, version = version + 1 WHERE public_id = ? AND status = 'RUNNING'",
        reason.length() <= ERROR_MAX ? reason : reason.substring(0, ERROR_MAX),
        Timestamp.from(at),
        Timestamp.from(at),
        runId);
  }

  /**
   * The runs that are RUNNING, oldest first: after a stop of the service they are the runs that
   * were interrupted (InterruptedRunRecovery).
   */
  public List<UUID> running() {
    return jdbc.queryForList(
        "SELECT public_id FROM extract_run WHERE status = 'RUNNING' ORDER BY started_at, id",
        UUID.class);
  }

  @Override
  public Optional<ExtractRun> find(UUID runId) {
    return jdbc.query(RUN_COLUMNS + " WHERE r.public_id = ?", JdbcExtractRuns::run, runId).stream()
        .findFirst();
  }

  @Override
  public RunPage page(int page, int size) {
    Long total = jdbc.queryForObject("SELECT count(*) FROM extract_run", Long.class);
    List<ExtractRun> items =
        jdbc.query(
            RUN_COLUMNS + " ORDER BY r.started_at DESC, r.id DESC LIMIT ? OFFSET ?",
            JdbcExtractRuns::run,
            size,
            (long) page * size);
    return new RunPage(items, total == null ? 0 : total);
  }

  @Override
  public Optional<byte[]> file(UUID runId) {
    return jdbc
        .query(
            "SELECT a.content FROM extract_archive a JOIN extract_run r ON r.id = a.extract_run_id"
                + " WHERE r.public_id = ? AND r.status = 'COMPLETED'",
            (rs, n) -> rs.getString("content").getBytes(StandardCharsets.US_ASCII),
            runId)
        .stream()
        .findFirst();
  }

  private Long runKey(UUID runId) {
    return jdbc.queryForObject("SELECT id FROM extract_run WHERE public_id = ?", Long.class, runId);
  }

  private static ExtractRun run(ResultSet rs, int n) throws SQLException {
    Timestamp ended = rs.getTimestamp("ended_at");
    Object generation = rs.getObject("generation");
    return new ExtractRun(
        rs.getObject("public_id", UUID.class),
        rs.getObject("business_date", LocalDate.class),
        rs.getString("period_yyddd"),
        rs.getString("status"),
        rs.getInt("row_count"),
        rs.getInt("future_enrolment_count"),
        generation == null ? null : ((Number) generation).intValue(),
        rs.getString("failure_reason"),
        rs.getTimestamp("started_at").toInstant(),
        ended == null ? null : ended.toInstant());
  }
}
