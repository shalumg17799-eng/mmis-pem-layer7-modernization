package gov.medicaid.pem.screening.infrastructure;

import gov.medicaid.pem.screening.application.ScreeningRuns;
import gov.medicaid.pem.screening.domain.Source;
import gov.medicaid.pem.screening.domain.SourceResult;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * screening_run (V1) and the run link of screening.run_id. Screened and failed are counted from the
 * run's screenings; read is screening_run.provider_count; skipped = read - screened (MAM-32 AC-5).
 */
@Repository
public class JdbcScreeningRuns implements ScreeningRuns {

  static final int ERROR_MAX = 500;

  private final JdbcTemplate jdbc;

  public JdbcScreeningRuns(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void create(UUID runId, LocalDate businessDate, Instant startedAt) {
    jdbc.update(
        "INSERT INTO screening_run (public_id, business_date, status, started_at)"
            + " VALUES (?, ?, 'RUNNING', ?)",
        runId,
        businessDate,
        Timestamp.from(startedAt));
  }

  @Override
  public void link(UUID screeningId, UUID runId) {
    jdbc.update(
        "UPDATE screening SET run_id = (SELECT id FROM screening_run WHERE public_id = ?)"
            + " WHERE public_id = ?",
        runId,
        screeningId);
  }

  @Override
  public boolean hasInitialScreening(String npi) {
    Integer n =
        jdbc.queryForObject(
            "SELECT count(*) FROM screening WHERE npi = ? AND reason = 'INITIAL'",
            Integer.class,
            npi);
    return n != null && n > 0;
  }

  @Override
  public List<Screened> screened(UUID runId) {
    Map<Long, String> npis = new LinkedHashMap<>();
    jdbc.query(
        "SELECT s.id, s.npi FROM screening s JOIN screening_run r ON r.id = s.run_id"
            + " WHERE r.public_id = ? ORDER BY s.npi, s.id",
        rs -> {
          npis.put(rs.getLong(1), rs.getString(2).trim());
        },
        runId);
    Map<Long, List<SourceResult>> results = new LinkedHashMap<>();
    npis.keySet().forEach(id -> results.put(id, new ArrayList<>()));
    jdbc.query(
        "SELECT x.screening_id, x.source, x.result, x.verdict_at, x.detail, x.error_code"
            + " FROM screening_result x JOIN screening s ON s.id = x.screening_id"
            + " JOIN screening_run r ON r.id = s.run_id WHERE r.public_id = ? ORDER BY x.id",
        rs -> {
          results
              .get(rs.getLong(1))
              .add(
                  new SourceResult(
                      Source.valueOf(rs.getString(2).trim()),
                      rs.getString(3).trim(),
                      rs.getTimestamp(4).toInstant(),
                      rs.getString(5),
                      rs.getString(6) == null ? null : rs.getString(6).trim()));
        },
        runId);
    List<Screened> out = new ArrayList<>();
    npis.forEach((id, npi) -> out.add(new Screened(npi, results.get(id))));
    return out;
  }

  @Override
  public void complete(UUID runId, int read, int failed, byte[] file, Instant endedAt) {
    jdbc.update(
        "UPDATE screening_run SET status = 'COMPLETED', provider_count = ?, fail_count = ?,"
            + " results_file = ?, ended_at = ?, updated_at = ?, version = version + 1"
            + " WHERE public_id = ?",
        read,
        failed,
        new String(file, StandardCharsets.US_ASCII),
        Timestamp.from(endedAt),
        Timestamp.from(endedAt),
        runId);
  }

  @Override
  public void fail(UUID runId, int read, String error, Instant endedAt) {
    String reason = error == null || error.isBlank() ? "failed" : error;
    jdbc.update(
        "UPDATE screening_run SET status = 'FAILED', provider_count = ?, failure_reason = ?,"
            + " ended_at = ?, updated_at = ?, version = version + 1"
            + " WHERE public_id = ? AND status = 'RUNNING'",
        read,
        reason.length() <= ERROR_MAX ? reason : reason.substring(0, ERROR_MAX),
        Timestamp.from(endedAt),
        Timestamp.from(endedAt),
        runId);
  }

  @Override
  public Optional<Run> find(UUID runId) {
    return jdbc
        .query(
            "SELECT r.public_id, r.business_date, r.status, r.provider_count, r.fail_count,"
                + " r.failure_reason, r.started_at, r.ended_at,"
                + " (SELECT count(*) FROM screening s WHERE s.run_id = r.id) AS screened"
                + " FROM screening_run r WHERE r.public_id = ?",
            JdbcScreeningRuns::run,
            runId)
        .stream()
        .findFirst();
  }

  @Override
  public Optional<byte[]> file(UUID runId) {
    return jdbc
        .query(
            "SELECT results_file FROM screening_run WHERE public_id = ? AND status = 'COMPLETED'"
                + " AND results_file IS NOT NULL",
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
        rs.getInt("provider_count"),
        rs.getInt("screened"),
        rs.getInt("fail_count"),
        rs.getString("failure_reason"),
        rs.getTimestamp("started_at").toInstant(),
        ended == null ? null : ended.toInstant());
  }
}
