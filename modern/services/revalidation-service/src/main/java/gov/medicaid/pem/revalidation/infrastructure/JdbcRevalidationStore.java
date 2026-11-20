package gov.medicaid.pem.revalidation.infrastructure;

import gov.medicaid.pem.revalidation.application.CaseCompletions;
import gov.medicaid.pem.revalidation.application.CasePage;
import gov.medicaid.pem.revalidation.application.CaseStages;
import gov.medicaid.pem.revalidation.application.CaseStore;
import gov.medicaid.pem.revalidation.application.ProviderTracking;
import gov.medicaid.pem.revalidation.application.SweepRun;
import gov.medicaid.pem.revalidation.application.SweepRuns;
import gov.medicaid.pem.revalidation.domain.CaseStatus;
import gov.medicaid.pem.revalidation.domain.RevalidationCase;
import gov.medicaid.pem.revalidation.domain.TrackedProvider;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** revalidation_db: tracked_provider, revalidation_case and sweep_run (Flyway V1, V2). */
@Repository
public class JdbcRevalidationStore
    implements CaseStore, SweepRuns, CaseCompletions, CaseStages, ProviderTracking {

  private final JdbcTemplate jdbc;
  private final Clock clock;

  public JdbcRevalidationStore(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  // ---- tracked providers (read model from ProviderStatusChanged)

  @Override
  public void track(TrackedProvider p) {
    jdbc.update(
        "INSERT INTO tracked_provider (npi, enroll_status, reval_due_date) VALUES (?, ?, ?)"
            + " ON CONFLICT (npi) DO UPDATE SET enroll_status = EXCLUDED.enroll_status,"
            + " reval_due_date = EXCLUDED.reval_due_date, updated_at = ?, version = tracked_provider.version + 1",
        p.npi(),
        p.enrollStatus(),
        p.dueDate() == null ? null : Date.valueOf(p.dueDate()),
        Timestamp.from(clock.instant()));
  }

  @Override
  public List<TrackedProvider> trackedProviders() {
    return jdbc.query(
        "SELECT npi, enroll_status, reval_due_date FROM tracked_provider ORDER BY npi",
        (rs, n) ->
            new TrackedProvider(
                rs.getString(1).trim(), rs.getString(2).trim(), date(rs.getDate(3))));
  }

  // ---- cases

  @Override
  public boolean hasOpenCase(String npi) {
    Integer n =
        jdbc.queryForObject(
            "SELECT count(*) FROM revalidation_case WHERE npi = ? AND status IN ('DUE','IN_SCREENING')",
            Integer.class,
            npi);
    return n != null && n > 0;
  }

  @Override
  public void open(RevalidationCase c, UUID runId) {
    jdbc.update(
        "INSERT INTO revalidation_case (public_id, npi, due_date, status, sweep_run_id, opened_at)"
            + " VALUES (?, ?, ?, ?, (SELECT id FROM sweep_run WHERE public_id = ?), ?)",
        c.caseId(),
        c.npi(),
        Date.valueOf(c.dueDate()),
        c.status().name(),
        runId,
        Timestamp.from(c.openedAt()));
  }

  @Override
  public CasePage find(String npi, CaseStatus status, int page, int size) {
    StringBuilder where = new StringBuilder(" WHERE 1 = 1");
    List<Object> args = new ArrayList<>();
    if (npi != null) {
      where.append(" AND npi = ?");
      args.add(npi);
    }
    if (status != null) {
      where.append(" AND status = ?");
      args.add(status.name());
    }
    Long total =
        jdbc.queryForObject(
            "SELECT count(*) FROM revalidation_case" + where, Long.class, args.toArray());
    List<Object> pageArgs = new ArrayList<>(args);
    pageArgs.add(size);
    pageArgs.add((long) page * size);
    List<RevalidationCase> items =
        jdbc.query(
            "SELECT public_id, npi, due_date, status, opened_at, closed_at FROM revalidation_case"
                + where
                + " ORDER BY due_date, npi, id LIMIT ? OFFSET ?",
            JdbcRevalidationStore::caseRow,
            pageArgs.toArray());
    return new CasePage(items, page, size, total == null ? 0 : total);
  }

  // ---- completion and stages (MAM-48, MAM-49)

  private static final String CASE_COLUMNS =
      "SELECT public_id, npi, due_date, status, opened_at, closed_at FROM revalidation_case";

  @Override
  public Optional<RevalidationCase> findCase(UUID caseId) {
    return jdbc
        .query(CASE_COLUMNS + " WHERE public_id = ?", JdbcRevalidationStore::caseRow, caseId)
        .stream()
        .findFirst();
  }

  @Override
  public Optional<RevalidationCase> openCaseOf(String npi) {
    return jdbc
        .query(
            CASE_COLUMNS + " WHERE npi = ? AND status IN ('DUE','IN_SCREENING')",
            JdbcRevalidationStore::caseRow,
            npi)
        .stream()
        .findFirst();
  }

  @Override
  public void close(UUID caseId, CaseStatus status, UUID screeningId, Instant at) {
    jdbc.update(
        "UPDATE revalidation_case SET status = ?, screening_id = ?, closed_at = ?, updated_at = ?,"
            + " version = version + 1 WHERE public_id = ? AND status IN ('DUE','IN_SCREENING')",
        status.name(),
        screeningId,
        Timestamp.from(at),
        Timestamp.from(at),
        caseId);
  }

  @Override
  public boolean markInScreening(UUID caseId) {
    return jdbc.update(
            "UPDATE revalidation_case SET status = 'IN_SCREENING', updated_at = ?, version = version + 1"
                + " WHERE public_id = ? AND status = 'DUE'",
            Timestamp.from(clock.instant()),
            caseId)
        == 1;
  }

  static RevalidationCase caseRow(ResultSet rs, int n) throws SQLException {
    Timestamp closed = rs.getTimestamp("closed_at");
    return new RevalidationCase(
        rs.getObject("public_id", UUID.class),
        rs.getString("npi").trim(),
        rs.getDate("due_date").toLocalDate(),
        CaseStatus.valueOf(rs.getString("status").trim()),
        rs.getTimestamp("opened_at").toInstant(),
        closed == null ? null : closed.toInstant());
  }

  // ---- sweep runs

  @Override
  public void create(SweepRun run) {
    jdbc.update(
        "INSERT INTO sweep_run (public_id, business_date, status, started_at) VALUES (?, ?, ?, ?)",
        run.runId(),
        Date.valueOf(run.businessDate()),
        run.status(),
        Timestamp.from(run.startedAt()));
  }

  @Override
  public void complete(UUID runId, int queued, int noDueDate, byte[] queueFile, Instant endedAt) {
    jdbc.update(
        "UPDATE sweep_run SET status = 'COMPLETED', queued_count = ?, no_due_date_count = ?,"
            + " queue_file = ?, ended_at = ?, updated_at = ?, version = version + 1 WHERE public_id = ?",
        queued,
        noDueDate,
        new String(queueFile, StandardCharsets.ISO_8859_1),
        Timestamp.from(endedAt),
        Timestamp.from(endedAt),
        runId);
  }

  @Override
  public void fail(UUID runId, String error, Instant endedAt) {
    jdbc.update(
        "UPDATE sweep_run SET status = 'FAILED', failure_reason = ?, ended_at = ?, updated_at = ?,"
            + " version = version + 1 WHERE public_id = ?",
        error.length() > 500 ? error.substring(0, 500) : error,
        Timestamp.from(endedAt),
        Timestamp.from(endedAt),
        runId);
  }

  @Override
  public Optional<SweepRun> find(UUID runId) {
    return jdbc
        .query(
            "SELECT public_id, business_date, status, queued_count, no_due_date_count, failure_reason,"
                + " started_at, ended_at FROM sweep_run WHERE public_id = ?",
            (rs, n) -> {
              Timestamp ended = rs.getTimestamp("ended_at");
              return new SweepRun(
                  rs.getObject("public_id", UUID.class),
                  rs.getDate("business_date").toLocalDate(),
                  rs.getString("status").trim(),
                  rs.getInt("queued_count"),
                  rs.getInt("no_due_date_count"),
                  rs.getString("failure_reason"),
                  rs.getTimestamp("started_at").toInstant(),
                  ended == null ? null : ended.toInstant());
            },
            runId)
        .stream()
        .findFirst();
  }

  @Override
  public Optional<byte[]> queueFile(UUID runId) {
    return jdbc
        .query(
            "SELECT queue_file FROM sweep_run WHERE public_id = ? AND status = 'COMPLETED'",
            (rs, n) -> {
              String file = rs.getString(1);
              return (file == null ? "" : file).getBytes(StandardCharsets.ISO_8859_1);
            },
            runId)
        .stream()
        .findFirst();
  }

  private static LocalDate date(Date d) {
    return d == null ? null : d.toLocalDate();
  }
}
