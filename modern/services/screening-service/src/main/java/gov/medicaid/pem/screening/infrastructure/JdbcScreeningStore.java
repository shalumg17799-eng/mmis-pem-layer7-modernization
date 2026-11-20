package gov.medicaid.pem.screening.infrastructure;

import gov.medicaid.pem.screening.application.Screening;
import gov.medicaid.pem.screening.application.ScreeningPage;
import gov.medicaid.pem.screening.application.ScreeningStore;
import gov.medicaid.pem.screening.domain.Source;
import gov.medicaid.pem.screening.domain.SourceResult;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * screening_db: one screening row and one screening_result row per source, only ever inserted
 * (D-018).
 */
@Repository
public class JdbcScreeningStore implements ScreeningStore {

  private final JdbcTemplate jdbc;

  public JdbcScreeningStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void save(Screening s) {
    Long id =
        jdbc.queryForObject(
            "INSERT INTO screening (public_id, npi, reason, status, result, started_at, completed_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id",
            Long.class,
            s.screeningId(),
            s.npi(),
            s.reason(),
            s.status(),
            s.result(),
            ts(s.startedAt()),
            ts(s.completedAt()));
    for (SourceResult r : s.results()) {
      jdbc.update(
          "INSERT INTO screening_result (screening_id, source, result, verdict_at, detail, error_code)"
              + " VALUES (?, ?, ?, ?, ?, ?)",
          id,
          r.source().name(),
          r.result(),
          ts(r.verdictAt()),
          r.detail(),
          r.errorCode());
    }
  }

  @Override
  public ScreeningPage find(String npi, String result, int page, int size) {
    StringBuilder where = new StringBuilder(" WHERE 1 = 1");
    List<Object> args = new ArrayList<>();
    if (npi != null) {
      where.append(" AND npi = ?");
      args.add(npi);
    }
    if (result != null) {
      where.append(" AND result = ?");
      args.add(result);
    }
    Long total =
        jdbc.queryForObject("SELECT count(*) FROM screening" + where, Long.class, args.toArray());
    List<Object> pageArgs = new ArrayList<>(args);
    pageArgs.add(size);
    pageArgs.add((long) page * size);
    List<Screening> items =
        load(
            "SELECT id, public_id, npi, reason, status, result, started_at, completed_at FROM screening"
                + where
                + " ORDER BY started_at DESC, id DESC LIMIT ? OFFSET ?",
            pageArgs.toArray());
    return new ScreeningPage(items, page, size, total == null ? 0 : total);
  }

  @Override
  public Optional<Screening> get(UUID screeningId) {
    return load(
            "SELECT id, public_id, npi, reason, status, result, started_at, completed_at FROM screening"
                + " WHERE public_id = ?",
            screeningId)
        .stream()
        .findFirst();
  }

  private record Row(long id, Screening head) {}

  private List<Screening> load(String sql, Object... args) {
    List<Row> rows =
        jdbc.query(
            sql,
            (rs, n) ->
                new Row(
                    rs.getLong("id"),
                    new Screening(
                        rs.getObject("public_id", UUID.class),
                        rs.getString("npi").trim(),
                        rs.getString("reason"),
                        rs.getString("status"),
                        rs.getString("result"),
                        List.of(),
                        instant(rs.getTimestamp("started_at")),
                        instant(rs.getTimestamp("completed_at")))),
            args);
    if (rows.isEmpty()) {
      return List.of();
    }
    Map<Long, List<SourceResult>> results = new HashMap<>();
    String marks = String.join(",", Collections.nCopies(rows.size(), "?"));
    jdbc.query(
        "SELECT screening_id, source, result, verdict_at, detail, error_code FROM screening_result"
            + " WHERE screening_id IN ("
            + marks
            + ") ORDER BY id",
        rs -> {
          String code = rs.getString("error_code");
          results
              .computeIfAbsent(rs.getLong("screening_id"), k -> new ArrayList<>())
              .add(
                  new SourceResult(
                      Source.valueOf(rs.getString("source")),
                      rs.getString("result").trim(),
                      instant(rs.getTimestamp("verdict_at")),
                      rs.getString("detail"),
                      code == null ? null : code.trim()));
        },
        rows.stream().map(Row::id).toArray());
    Map<Long, Screening> out = new LinkedHashMap<>();
    for (Row r : rows) {
      Screening h = r.head();
      out.put(
          r.id(),
          new Screening(
              h.screeningId(),
              h.npi(),
              h.reason(),
              h.status(),
              h.result(),
              results.getOrDefault(r.id(), List.of()),
              h.startedAt(),
              h.completedAt()));
    }
    return List.copyOf(out.values());
  }

  private static Timestamp ts(Instant i) {
    return i == null ? null : Timestamp.from(i);
  }

  private static Instant instant(Timestamp t) {
    return t == null ? null : t.toInstant();
  }
}
